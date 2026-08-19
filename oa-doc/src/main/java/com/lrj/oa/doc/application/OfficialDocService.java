package com.lrj.oa.doc.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.common.id.SegmentIdGenerator;
import com.lrj.oa.doc.api.dto.DocDtos;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 公文流转：拟稿 → 送审 → 核发（生成文号） → 归档。
 *
 * <p><b>文号在核发时才生成，不在拟稿时</b>：拟稿会被撤销、会被驳回，
 * 那些稿子若都占了文号，年度文号序列里就会出现一堆空洞。而公文文号
 * 在档案管理上要求<b>年内连续</b>，空洞是要被追问的。
 *
 * <p>因此文号用 {@code step=1} 的号段（每次推进数据库），而不是本地缓存 1000 个 ——
 * 用性能换连续性。公文一年几百份，这个代价完全可以承受；单据号一年几十万份，
 * 才需要号段缓存。同一个生成器，两种取法，取舍由调用方显式做出。
 */
@Service
public class OfficialDocService {

    private static final Logger log = LoggerFactory.getLogger(OfficialDocService.class);

    /** 合法的状态迁移。写成显式表而不是散在各方法里的 if，是为了让"能不能这么走"一眼可查。 */
    private static final Set<String> ISSUABLE = Set.of("DRAFT", "REVIEWING");

    private final JdbcTemplate jdbc;
    private final SegmentIdGenerator ids;
    private final String docPrefix;

    public OfficialDocService(JdbcTemplate jdbc, SegmentIdGenerator ids,
                              @Value("${oa.doc.number-prefix:司办发}") String docPrefix) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.docPrefix = docPrefix;
    }

    @Transactional
    public long draft(DocDtos.DraftDoc cmd) {
        UserContext ctx = UserContextHolder.require();
        if (!"IN".equals(cmd.direction()) && !"OUT".equals(cmd.direction())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "direction 必须是 IN 或 OUT");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO oa_doc.official_doc
                    (direction, title, body, doc_type, urgency, secrecy, source_org,
                     drafter_id, status, org_id, org_path)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)
                RETURNING id
                """, Long.class, cmd.direction(), cmd.title(), cmd.body(),
                cmd.docType() == null ? "NOTICE" : cmd.docType(),
                cmd.urgency() == null ? "NORMAL" : cmd.urgency(),
                cmd.secrecy() == null ? "PUBLIC" : cmd.secrecy(),
                cmd.sourceOrg(), ctx.userId(), ctx.primaryOrgId(), ctx.primaryOrgPath());
        if (id == null) throw BusinessException.of(ResultCode.INTERNAL_ERROR, "公文写入失败");
        logFlow(id, "SUBMIT", ctx.userId(), null, "DRAFT", null);
        return id;
    }

    /** 核发：生成文号并置为 ISSUED。文号一旦生成不可更改。 */
    @Transactional
    public String issue(long docId) {
        UserContext ctx = UserContextHolder.require();
        String status = jdbc.query("SELECT status FROM oa_doc.official_doc WHERE id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getString(1) : null, docId);
        if (status == null) throw BusinessException.of(ResultCode.NOT_FOUND, "公文不存在");
        if (!ISSUABLE.contains(status)) {
            throw BusinessException.of(ResultCode.CONFLICT, "当前状态 " + status + " 不可核发");
        }
        String number = ids.nextDocNumber(docPrefix, "DOC");
        jdbc.update("UPDATE oa_doc.official_doc SET doc_number = ?, status = 'ISSUED', issued_at = now()"
                + " WHERE id = ?", number, docId);
        logFlow(docId, "ISSUE", ctx.userId(), status, "ISSUED", number);
        log.info("公文 {} 核发，文号 {}", docId, number);
        return number;
    }

    @Transactional
    public void archive(long docId) {
        UserContext ctx = UserContextHolder.require();
        int n = jdbc.update("UPDATE oa_doc.official_doc SET status = 'ARCHIVED', archived_at = now()"
                + " WHERE id = ? AND status = 'ISSUED'", docId);
        if (n == 0) throw BusinessException.of(ResultCode.CONFLICT, "只有已核发的公文才能归档");
        logFlow(docId, "ARCHIVE", ctx.userId(), "ISSUED", "ARCHIVED", null);
    }

    /** 全文检索。ES 是可选项，未上 ES 时降级为 PG 的 GIN 索引（FINAL_PLAN 开放项 4）。 */
    public List<DocDtos.DocView> search(String keyword, String status, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, direction, doc_number, title, body, doc_type, urgency, secrecy,
                       status, drafter_id, issued_at, archived_at, org_id
                  FROM oa_doc.official_doc WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            // 动态拼而不是 "(#{kw} IS NULL OR ...)"：后者让 PG 推断不出参数类型，
            // 整条查询报 could not determine data type of parameter（Phase 2 踩过两次）。
            //
            // ★ 用 ILIKE 而不是 tsvector：PG 自带的分词配置切不了中文，
            //   to_tsvector('simple','关于国庆放假的通知') 是一个整 token，搜"放假"永远 0 行 ——
            //   接口返回 200 + 空数组，看起来像"确实没有这份文件"，是最难察觉的一种错。
            //   pg_trgm 的三元组索引让 ILIKE '%词%' 能走索引（见 V62）。
            //   要真正的中文分词得上 zhparser 或 ES（FINAL_PLAN 里是可选项）。
            sql.append(" AND (title ILIKE ? OR coalesce(body,'') ILIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 200));

        List<DocDtos.DocView> out = new ArrayList<>();
        jdbc.query(sql.toString(), (RowCallbackHandler) rs -> out.add(new DocDtos.DocView(
                        rs.getLong("id"), rs.getString("direction"), rs.getString("doc_number"),
                        rs.getString("title"), rs.getString("body"), rs.getString("doc_type"),
                        rs.getString("urgency"), rs.getString("secrecy"), rs.getString("status"),
                        rs.getString("drafter_id"), rs.getObject("issued_at", OffsetDateTime.class),
                        rs.getObject("archived_at", OffsetDateTime.class), rs.getObject("org_id", Long.class))),
                args.toArray());
        return out;
    }

    private void logFlow(long docId, String action, String actor, String from, String to, String remark) {
        jdbc.update("""
                INSERT INTO oa_doc.doc_flow_log(doc_id, action, actor_id, from_status, to_status, remark)
                VALUES (?, ?, ?, ?, ?, ?)
                """, docId, action, actor, from, to, remark);
    }
}
