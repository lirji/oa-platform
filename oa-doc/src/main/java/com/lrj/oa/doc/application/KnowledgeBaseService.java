package com.lrj.oa.doc.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.doc.api.dto.DocDtos;
import com.lrj.oa.doc.infrastructure.authz.KbAuthorizer;
import com.lrj.oa.doc.infrastructure.mapper.KnowledgeBaseMapper;
import com.lrj.oa.security.annotation.ObjectScope;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库 / 网盘。
 *
 * <p>注意这里有<b>两层</b>授权，缺一不可：
 * <ul>
 *   <li>接口权限 {@code @RequiresPerm("oa:kb:read")} —— 你有没有"使用知识库"这个功能；</li>
 *   <li>对象权限 {@link KbAuthorizer} —— 你能不能看<b>这一份</b>文档。</li>
 * </ul>
 * 只做第一层是典型的越权：所有员工都有 oa:kb:read，若不再判对象级，
 * 任何人换个 id 就能读到别人的私有文档（IDOR）。
 */
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper mapper;
    private final KbAuthorizer authorizer;

    public KnowledgeBaseService(KnowledgeBaseMapper mapper, KbAuthorizer authorizer) {
        this.mapper = mapper;
        this.authorizer = authorizer;
    }

    public String authorizerName() { return authorizer.name(); }

    @Transactional
    @ObjectScope(permission = "oa:kb:write", tables = "oa_doc.kb_doc",
            strategy = ObjectScope.Strategy.OWNER, reason = "新文档所有者固定为当前用户")
    public long create(DocDtos.CreateKbDoc cmd) {
        UserContext ctx = UserContextHolder.require();
        Long id = mapper.insert(TenantContext.get(), cmd.folderId(), cmd.title(), cmd.summary(),
                cmd.body(), cmd.fileKey(), ctx.userId());
        if (id == null) throw BusinessException.of(ResultCode.INTERNAL_ERROR, "知识库文档写入失败");
        return id;
    }

    /** 读一篇。★ 每次都过对象级判权 —— by-id 查询是 IDOR 的主战场。 */
    @ObjectScope(permission = "oa:kb:read", tables = {"oa_doc.kb_doc", "oa_doc.kb_share"},
            strategy = ObjectScope.Strategy.ACL, reason = "读取前由 KbAuthorizer 校验文档 ACL")
    public DocDtos.KbDocView read(long docId) {
        UserContext ctx = UserContextHolder.require();
        if (!authorizer.canAccess(ctx.userId(), "DOC", docId, "READ")) {
            // 用 403 而不是 404：这里不做"存在性隐藏"，因为知识库的文档 id 本就会
            // 在链接里流传，假装不存在只会让用户困惑。真正的机密文档靠 secrecy 分级另行处理。
            throw BusinessException.of(ResultCode.PERM_DENIED, "你没有这份文档的访问权限");
        }
        KnowledgeBaseMapper.DocRow row = mapper.selectById(TenantContext.get(), docId);
        if (row == null) throw BusinessException.of(ResultCode.NOT_FOUND, "文档不存在");
        return view(row);
    }

    @Transactional
    @ObjectScope(permission = "oa:kb:read", tables = {"oa_doc.kb_doc", "oa_doc.kb_share"},
            strategy = ObjectScope.Strategy.ACL, reason = "更新前由 KbAuthorizer 校验 WRITE ACL")
    public void update(long docId, DocDtos.CreateKbDoc cmd) {
        UserContext ctx = UserContextHolder.require();
        if (!authorizer.canAccess(ctx.userId(), "DOC", docId, "WRITE")) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "你没有这份文档的编辑权限");
        }
        int n = mapper.update(TenantContext.get(), docId, cmd.title(), cmd.summary(), cmd.body(), cmd.fileKey());
        if (n == 0) throw BusinessException.of(ResultCode.NOT_FOUND, "文档不存在");
    }

    /**
     * 共享。只有 owner 或有 WRITE 的人能再分享出去 ——
     * 否则一个只读接收者可以把文档转授给全公司，共享链路上就没有任何收敛点了。
     */
    @Transactional
    @ObjectScope(permission = "oa:kb:share", tables = {"oa_doc.kb_doc", "oa_doc.kb_share"},
            strategy = ObjectScope.Strategy.ACL, reason = "共享前由 KbAuthorizer 校验 WRITE ACL")
    public void share(DocDtos.ShareKb cmd) {
        UserContext ctx = UserContextHolder.require();
        if (!authorizer.canAccess(ctx.userId(), cmd.resourceType(), cmd.resourceId(), "WRITE")) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "只有 owner 或可编辑者才能共享");
        }
        String orgPath = null;
        if ("ORG".equals(cmd.subjectType())) {
            if (cmd.subjectId() == null) {
                throw BusinessException.of(ResultCode.BAD_REQUEST, "ORG 共享必须指定组织 id");
            }
            orgPath = mapper.orgPath(TenantContext.get(), Long.valueOf(cmd.subjectId()));
            if (orgPath == null) throw BusinessException.of(ResultCode.NOT_FOUND, "组织不存在");
            // 冗余 org_path 是刻意的：判权时走前缀匹配，绝不在运行时去 join 组织表
            // （跨 schema join 被禁，且那会把每次判权变成一次树查询）。
        }
        mapper.upsertShare(TenantContext.get(), cmd.resourceType(), cmd.resourceId(), cmd.subjectType(),
                cmd.subjectId(), orgPath, cmd.level() == null ? "READ" : cmd.level(), ctx.userId());
    }

    /** 我能看到的文档列表。逐条过判权，而不是"先查全部再让前端隐藏"。 */
    @ObjectScope(permission = "oa:kb:read", tables = {"oa_doc.kb_doc", "oa_doc.kb_share"},
            strategy = ObjectScope.Strategy.ACL, reason = "候选文档逐条由 KbAuthorizer 过滤")
    public List<DocDtos.KbDocView> listVisible(int limit) {
        UserContext ctx = UserContextHolder.require();
        List<KnowledgeBaseMapper.DocRow> all = mapper.recent(TenantContext.get(),
                Math.min(Math.max(limit, 1), 200) * 5L);   // 多取一些，过滤后再截断
        return all.stream().map(KnowledgeBaseService::view)
                .filter(d -> authorizer.canAccess(ctx.userId(), "DOC", d.id(), "READ"))
                .limit(Math.min(Math.max(limit, 1), 200))
                .toList();
    }

    @ObjectScope(permission = "oa:kb:share", tables = {"oa_doc.kb_doc", "oa_doc.kb_share"},
            strategy = ObjectScope.Strategy.ACL, reason = "访问解释由 KbAuthorizer 生成且仅共享管理员可调用")
    public DocDtos.KbAccessExplain explain(long docId, String userId) {
        return authorizer.explain(userId, docId);
    }

    private static DocDtos.KbDocView view(KnowledgeBaseMapper.DocRow row) {
        return new DocDtos.KbDocView(row.id, row.folderId, row.title, row.summary, row.body,
                row.ownerId, row.version, row.updatedAt);
    }
}
