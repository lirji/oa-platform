package com.lrj.oa.doc.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.common.id.SegmentIdGenerator;
import com.lrj.oa.doc.api.dto.DocDtos;
import com.lrj.oa.doc.infrastructure.mapper.OfficialDocMapper;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final OfficialDocMapper mapper;
    private final SegmentIdGenerator ids;
    private final String docPrefix;

    public OfficialDocService(OfficialDocMapper mapper, SegmentIdGenerator ids,
                              @Value("${oa.doc.number-prefix:司办发}") String docPrefix) {
        this.mapper = mapper;
        this.ids = ids;
        this.docPrefix = docPrefix;
    }

    @Transactional
    public long draft(DocDtos.DraftDoc cmd) {
        UserContext ctx = UserContextHolder.require();
        if (!"IN".equals(cmd.direction()) && !"OUT".equals(cmd.direction())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "direction 必须是 IN 或 OUT");
        }
        Long id = mapper.insertDraft(TenantContext.get(), cmd.direction(), cmd.title(), cmd.body(),
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
    @DataScope(permission = "oa:doc:issue", table = "oa_doc.official_doc", alias = "d",
            module = "doc", userColumn = "drafter_id")
    public String issue(long docId) {
        UserContext ctx = UserContextHolder.require();
        String status = mapper.selectStatusForUpdate(TenantContext.get(), docId);
        if (status == null) throw BusinessException.of(ResultCode.NOT_FOUND, "公文不存在");
        if (!ISSUABLE.contains(status)) {
            throw BusinessException.of(ResultCode.CONFLICT, "当前状态 " + status + " 不可核发");
        }
        String number = ids.nextDocNumber(docPrefix, "DOC");
        mapper.issue(TenantContext.get(), docId, number);
        logFlow(docId, "ISSUE", ctx.userId(), status, "ISSUED", number);
        log.info("公文 {} 核发，文号 {}", docId, number);
        return number;
    }

    @Transactional
    @DataScope(permission = "oa:doc:archive", table = "oa_doc.official_doc", alias = "d",
            module = "doc", userColumn = "drafter_id")
    public void archive(long docId) {
        UserContext ctx = UserContextHolder.require();
        int n = mapper.archive(TenantContext.get(), docId);
        if (n == 0) throw BusinessException.of(ResultCode.CONFLICT, "只有已核发的公文才能归档");
        logFlow(docId, "ARCHIVE", ctx.userId(), "ISSUED", "ARCHIVED", null);
    }

    /** 全文检索。ES 是可选项，未上 ES 时降级为 PG 的 GIN 索引（FINAL_PLAN 开放项 4）。 */
    @DataScope(permission = "oa:doc:read", table = "oa_doc.official_doc", alias = "d",
            module = "doc", userColumn = "drafter_id")
    public List<DocDtos.DocView> search(String keyword, String status, int limit) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return mapper.search(TenantContext.get(), normalizedKeyword, status,
                        Math.min(Math.max(limit, 1), 200)).stream()
                .map(r -> new DocDtos.DocView(r.id, r.direction, r.docNumber, r.title, r.body,
                        r.docType, r.urgency, r.secrecy, r.status, r.drafterId,
                        r.issuedAt, r.archivedAt, r.orgId))
                .toList();
    }

    private void logFlow(long docId, String action, String actor, String from, String to, String remark) {
        mapper.insertFlow(docId, action, actor, from, to, remark);
    }
}
