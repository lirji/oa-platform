package com.lrj.oa.flow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.common.id.SegmentIdGenerator;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用业务单据：出差 / 加班 / 调休 / 报销 / 借款 / 用印 / 合同 / 采购 / 入职 / 离职 / 调岗。
 *
 * <p><b>一份实现服务 11 类单据</b>。它们的差别只在两处：表单字段（由 {@code form_template}
 * 的 JSON Schema 描述）和审批级数怎么算（由 {@code approval_level_rule} 描述）。
 * 流转、留痕、发件箱、待办投影完全一样。
 *
 * <p>复制 11 份的代价不在写的时候，而在<b>以后每改一次审批链逻辑要改 11 处</b>，
 * 且必然有一处忘了改 —— 那一处会安静地按旧规则跑下去，直到某天有人发现
 * "为什么这张单子只走了一级"。
 *
 * <p>请假不走这里：它有额度冻结/扣减/释放这套独有的强一致语义，
 * 塞进通用 jsonb 会让那套约束无处安放。<b>大部分通用 + 少数特殊单独处理</b>
 * 比"全部通用"和"全部特殊"都更诚实。
 */
@Service
public class BusinessDocService {

    private static final Logger log = LoggerFactory.getLogger(BusinessDocService.class);
    private static final String PROCESS_KEY = "oaGenericApproval";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final ApprovalService approvalService;
    private final ApproverResolver approverResolver;
    private final SegmentIdGenerator ids;

    public BusinessDocService(JdbcTemplate jdbc, ApprovalService approvalService,
                              ApproverResolver approverResolver, SegmentIdGenerator ids) {
        this.jdbc = jdbc;
        this.approvalService = approvalService;
        this.approverResolver = approverResolver;
        this.ids = ids;
    }

    /**
     * 把 11 类单据的结束回调全部注册到同一个 handler。
     *
     * <p>类型清单从<b>规则表</b>读，不写死在代码里 —— 否则以后往
     * {@code approval_level_rule} 插一类新单据，提单能成功、审批也能走完，
     * 唯独单据状态永远停在 PENDING（没人注册回调）。那是最难查的一类不一致：
     * 每一步看起来都成功了。
     */
    @jakarta.annotation.PostConstruct
    void registerFinishHandlers() {
        List<String> types = jdbc.queryForList(
                "SELECT biz_type FROM oa_flow.approval_level_rule", String.class);
        for (String t : types) {
            approvalService.registerFinishHandler(t, (businessKey, outcome) -> onFinished(businessKey, outcome));
        }
        log.info("通用单据结束回调已注册 {} 类：{}", types.size(), types);
    }

    public record SubmitDoc(String bizType, Map<String, Object> formData) {}

    public record DocView(Long id, String bizType, String docNo, String title, String summary,
                          Map<String, Object> formData, BigDecimal amount, BigDecimal days,
                          String status, List<String> approverChain, OffsetDateTime createdAt) {}

    @Transactional
    public DocView submit(SubmitDoc cmd) {
        UserContext ctx = UserContextHolder.require();
        String bizType = cmd.bizType() == null ? "" : cmd.bizType().toUpperCase();

        Map<String, Object> form = cmd.formData() == null ? Map.of() : cmd.formData();
        String templateName = validateAgainstTemplate(bizType, form);

        BigDecimal amount = numberOf(form, "amount");
        BigDecimal days = numberOf(form, "days");
        int levels = levelsFor(bizType, amount, days);
        List<String> chain = approverResolver.resolveChain(ctx.userId(), levels);
        if (chain.isEmpty()) {
            // 没有审批人是【业务配置问题】，不是系统错误：这个人没有汇报线。
            // 悄悄让单据自动通过是最坏的选择 —— 那等于给没人管的员工开了后门。
            throw BusinessException.of(ResultCode.FLOW_START_FAILED,
                    "算不出审批人：请先为你配置汇报线上级");
        }

        String docNo = ids.next(bizType);
        String title = ctx.username() + " 的" + templateName;
        String summary = summarize(bizType, form, amount, days);

        Long id = jdbc.queryForObject("""
                INSERT INTO oa_flow.business_doc
                    (biz_type, doc_no, applicant_id, applicant_name, title, summary,
                     form_data, amount, days, status, org_id, org_path)
                VALUES (?,?,?,?,?,?,?::jsonb,?,?, 'PENDING', ?,?)
                RETURNING id
                """, Long.class, bizType, docNo, ctx.userId(), ctx.username(), title, summary,
                writeJson(form), amount, days, ctx.primaryOrgId(), ctx.primaryOrgPath());
        if (id == null) throw BusinessException.of(ResultCode.INTERNAL_ERROR, "单据写入失败");

        approvalService.submit(new ApprovalService.SubmitRequest(
                bizType, docNo, PROCESS_KEY, bizType, form, title, summary, chain));

        log.info("{} 单据 {} 已提交：{} 级审批 {}", bizType, docNo, levels, chain);
        return new DocView(id, bizType, docNo, title, summary, form, amount, days, "PENDING", chain,
                OffsetDateTime.now());
    }

    /**
     * 按 {@code form_template} 的 JSON Schema 校验。
     *
     * <p>只校验 required 与基本类型/下界 —— 不引 JSON Schema 校验库。
     * 引一个完整实现要处理 $ref、allOf、format 等等，而首版模板里根本没用到它们；
     * 引进来的复杂度全是为将来可能不会发生的需求付的。真需要时再换，接口不变。
     */
    private String validateAgainstTemplate(String bizType, Map<String, Object> form) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT name, schema_json FROM oa_flow.form_template
                 WHERE code = ? AND status = 'PUBLISHED' ORDER BY version DESC LIMIT 1
                """, bizType);
        if (rows.isEmpty()) {
            throw BusinessException.of(ResultCode.FORM_TEMPLATE_INVALID,
                    "没有已发布的表单模板: " + bizType);
        }
        String name = (String) rows.get(0).get("name");
        JsonNode schema = readJson(String.valueOf(rows.get(0).get("schema_json")));

        List<String> missing = new ArrayList<>();
        JsonNode required = schema.path("required");
        for (JsonNode f : required) {
            Object v = form.get(f.asText());
            if (v == null || (v instanceof String s && s.isBlank())) missing.add(f.asText());
        }
        if (!missing.isEmpty()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "缺少必填字段: " + String.join("、", missing));
        }
        JsonNode props = schema.path("properties");
        for (Map.Entry<String, Object> e : form.entrySet()) {
            JsonNode p = props.path(e.getKey());
            if (p.isMissingNode()) continue;   // 模板没声明的字段放行，多传不算错
            if ("number".equals(p.path("type").asText()) || "integer".equals(p.path("type").asText())) {
                BigDecimal v = toBigDecimal(e.getValue());
                if (v == null) {
                    throw BusinessException.of(ResultCode.BAD_REQUEST, e.getKey() + " 必须是数字");
                }
                if (p.has("minimum") && v.compareTo(new BigDecimal(p.get("minimum").asText())) < 0) {
                    throw BusinessException.of(ResultCode.BAD_REQUEST,
                            e.getKey() + " 不能小于 " + p.get("minimum").asText());
                }
            }
        }
        return name;
    }

    /** 按规则表算审批级数。规则进表是为了"报销超过 5 万加一级"这种调整不必发版。 */
    int levelsFor(String bizType, BigDecimal amount, BigDecimal days) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT driver, thresholds FROM oa_flow.approval_level_rule WHERE biz_type = ?", bizType);
        if (rows.isEmpty()) {
            throw BusinessException.of(ResultCode.FLOW_START_FAILED,
                    "没有为 " + bizType + " 配置审批级数规则");
        }
        String driver = (String) rows.get(0).get("driver");
        JsonNode thresholds = readJson(String.valueOf(rows.get(0).get("thresholds")));
        BigDecimal metric = switch (driver) {
            case "AMOUNT" -> amount == null ? BigDecimal.ZERO : amount;
            case "DAYS" -> days == null ? BigDecimal.ZERO : days;
            default -> null;   // FIXED：不看量
        };
        for (JsonNode t : thresholds) {
            if (!t.has("lte")) return t.path("levels").asInt(1);          // 兜底档
            if (metric == null) continue;
            if (metric.compareTo(new BigDecimal(t.get("lte").asText())) <= 0) {
                return t.path("levels").asInt(1);
            }
        }
        // 规则表没写兜底档 = 大额单据算不出级数。这是配置错误，必须显式失败，
        // 而不是默默给一个 1 级 —— 一张五十万的合同走一级审批是事故。
        throw BusinessException.of(ResultCode.FLOW_START_FAILED,
                bizType + " 的审批级数规则缺少兜底档（最后一条不应带 lte）");
    }

    public List<DocView> myDocs(String bizType, int limit) {
        UserContext ctx = UserContextHolder.require();
        StringBuilder sql = new StringBuilder("""
                SELECT id, biz_type, doc_no, title, summary, form_data, amount, days, status, created_at
                  FROM oa_flow.business_doc WHERE applicant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(ctx.userId());
        if (bizType != null && !bizType.isBlank()) {
            sql.append(" AND biz_type = ?");
            args.add(bizType.toUpperCase());
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 200));

        List<DocView> out = new ArrayList<>();
        jdbc.query(sql.toString(), (RowCallbackHandler) rs -> out.add(new DocView(
                        rs.getLong("id"), rs.getString("biz_type"), rs.getString("doc_no"),
                        rs.getString("title"), rs.getString("summary"),
                        readMap(rs.getString("form_data")),
                        rs.getBigDecimal("amount"), rs.getBigDecimal("days"),
                        rs.getString("status"), null,
                        rs.getObject("created_at", OffsetDateTime.class))),
                args.toArray());
        return out;
    }

    /** 审批结束回调。由 ApprovalService 按 bizType 派发。 */
    public void onFinished(String docNo, String outcome) {
        String status = "REJECTED".equalsIgnoreCase(outcome) ? "REJECTED" : "APPROVED";
        jdbc.update("UPDATE oa_flow.business_doc SET status = ?, finished_at = now()"
                + " WHERE doc_no = ? AND status = 'PENDING'", status, docNo);
        log.info("单据 {} 结束：{}", docNo, status);
    }

    /** 一种单据类型：字段由 schema 描述、审批级数由 rule 描述。前端据此渲染表单，不必每类写一个页面。 */
    public record DocType(String code, String name, int version, JsonNode schema,
                          String driver, String rule) {}

    /**
     * 支持的单据类型（前端渲染表单用）。
     *
     * <p>★ schema 必须<b>解析成 JsonNode 再返回</b>：jsonb 列经 JDBC 出来是 PGobject，
     * 直接进 Map 会被序列化成 {@code {"type":"jsonb","value":"<JSON 字符串>"}} ——
     * 前端拿到的是一个套了两层的字符串，要二次 parse 才能用，而这件事没有任何地方会提示它。
     */
    public List<DocType> supportedTypes() {
        return jdbc.query("""
                SELECT t.code, t.name, t.version, t.schema_json, r.driver, r.description AS rule
                  FROM oa_flow.form_template t
                  LEFT JOIN oa_flow.approval_level_rule r ON r.biz_type = t.code
                 WHERE t.status = 'PUBLISHED' AND t.code <> 'LEAVE'
                 ORDER BY t.code
                """, (rs, i) -> new DocType(rs.getString("code"), rs.getString("name"),
                        rs.getInt("version"), readJson(rs.getString("schema_json")),
                        rs.getString("driver"), rs.getString("rule")));
    }

    // ───────────────────────────────── 工具

    private static String summarize(String bizType, Map<String, Object> f,
                                    BigDecimal amount, BigDecimal days) {
        if (amount != null) return "金额 " + amount.toPlainString();
        if (days != null) return "共 " + days.toPlainString() + " 天";
        Object reason = f.get("reason");
        return reason == null ? bizType : String.valueOf(reason);
    }

    private static BigDecimal numberOf(Map<String, Object> form, String key) {
        return toBigDecimal(form.get(key));
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String writeJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "表单数据无法序列化");
        }
    }

    private JsonNode readJson(String s) {
        try {
            return json.readTree(s);
        } catch (Exception e) {
            throw BusinessException.of(ResultCode.FORM_TEMPLATE_INVALID, "模板 JSON 不合法");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String s) {
        if (s == null) return new LinkedHashMap<>();
        try {
            return json.readValue(s, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
