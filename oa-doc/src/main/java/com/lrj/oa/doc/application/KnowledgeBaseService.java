package com.lrj.oa.doc.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.doc.api.dto.DocDtos;
import com.lrj.oa.doc.infrastructure.authz.KbAuthorizer;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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

    private final JdbcTemplate jdbc;
    private final KbAuthorizer authorizer;

    public KnowledgeBaseService(JdbcTemplate jdbc, KbAuthorizer authorizer) {
        this.jdbc = jdbc;
        this.authorizer = authorizer;
    }

    public String authorizerName() { return authorizer.name(); }

    @Transactional
    public long create(DocDtos.CreateKbDoc cmd) {
        UserContext ctx = UserContextHolder.require();
        Long id = jdbc.queryForObject("""
                INSERT INTO oa_doc.kb_doc(folder_id, title, summary, body, file_key, owner_id)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, cmd.folderId(), cmd.title(), cmd.summary(), cmd.body(),
                cmd.fileKey(), ctx.userId());
        if (id == null) throw BusinessException.of(ResultCode.INTERNAL_ERROR, "知识库文档写入失败");
        return id;
    }

    /** 读一篇。★ 每次都过对象级判权 —— by-id 查询是 IDOR 的主战场。 */
    public DocDtos.KbDocView read(long docId) {
        UserContext ctx = UserContextHolder.require();
        if (!authorizer.canAccess(ctx.userId(), "DOC", docId, "READ")) {
            // 用 403 而不是 404：这里不做"存在性隐藏"，因为知识库的文档 id 本就会
            // 在链接里流传，假装不存在只会让用户困惑。真正的机密文档靠 secrecy 分级另行处理。
            throw BusinessException.of(ResultCode.PERM_DENIED, "你没有这份文档的访问权限");
        }
        DocDtos.KbDocView[] holder = new DocDtos.KbDocView[1];
        jdbc.query("""
                SELECT id, folder_id, title, summary, body, owner_id, version, updated_at
                  FROM oa_doc.kb_doc WHERE id = ?
                """, (RowCallbackHandler) rs -> holder[0] = new DocDtos.KbDocView(
                        rs.getLong("id"), rs.getObject("folder_id", Long.class), rs.getString("title"),
                        rs.getString("summary"), rs.getString("body"), rs.getString("owner_id"),
                        rs.getInt("version"), rs.getObject("updated_at", OffsetDateTime.class)), docId);
        if (holder[0] == null) throw BusinessException.of(ResultCode.NOT_FOUND, "文档不存在");
        return holder[0];
    }

    @Transactional
    public void update(long docId, DocDtos.CreateKbDoc cmd) {
        UserContext ctx = UserContextHolder.require();
        if (!authorizer.canAccess(ctx.userId(), "DOC", docId, "WRITE")) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "你没有这份文档的编辑权限");
        }
        int n = jdbc.update("""
                UPDATE oa_doc.kb_doc SET title = ?, summary = ?, body = ?, file_key = ?,
                       version = version + 1, updated_at = now()
                 WHERE id = ?
                """, cmd.title(), cmd.summary(), cmd.body(), cmd.fileKey(), docId);
        if (n == 0) throw BusinessException.of(ResultCode.NOT_FOUND, "文档不存在");
    }

    /**
     * 共享。只有 owner 或有 WRITE 的人能再分享出去 ——
     * 否则一个只读接收者可以把文档转授给全公司，共享链路上就没有任何收敛点了。
     */
    @Transactional
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
            orgPath = jdbc.query("SELECT path FROM oa_org.org_unit WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null, Long.valueOf(cmd.subjectId()));
            if (orgPath == null) throw BusinessException.of(ResultCode.NOT_FOUND, "组织不存在");
            // 冗余 org_path 是刻意的：判权时走前缀匹配，绝不在运行时去 join 组织表
            // （跨 schema join 被禁，且那会把每次判权变成一次树查询）。
        }
        jdbc.update("""
                INSERT INTO oa_doc.kb_share
                    (resource_type, resource_id, subject_type, subject_id, subject_org_path, level, granted_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, resource_type, resource_id, subject_type, subject_id)
                DO UPDATE SET level = EXCLUDED.level, granted_by = EXCLUDED.granted_by
                """, cmd.resourceType(), cmd.resourceId(), cmd.subjectType(), cmd.subjectId(),
                orgPath, cmd.level() == null ? "READ" : cmd.level(), ctx.userId());
    }

    /** 我能看到的文档列表。逐条过判权，而不是"先查全部再让前端隐藏"。 */
    public List<DocDtos.KbDocView> listVisible(int limit) {
        UserContext ctx = UserContextHolder.require();
        List<DocDtos.KbDocView> all = new ArrayList<>();
        jdbc.query("""
                SELECT id, folder_id, title, summary, owner_id, version, updated_at
                  FROM oa_doc.kb_doc ORDER BY updated_at DESC LIMIT ?
                """, (RowCallbackHandler) rs -> all.add(new DocDtos.KbDocView(
                        rs.getLong("id"), rs.getObject("folder_id", Long.class), rs.getString("title"),
                        rs.getString("summary"), null, rs.getString("owner_id"),
                        rs.getInt("version"), rs.getObject("updated_at", OffsetDateTime.class))),
                Math.min(Math.max(limit, 1), 200) * 5L);   // 多取一些，过滤后再截断
        return all.stream()
                .filter(d -> authorizer.canAccess(ctx.userId(), "DOC", d.id(), "READ"))
                .limit(Math.min(Math.max(limit, 1), 200))
                .toList();
    }

    public DocDtos.KbAccessExplain explain(long docId, String userId) {
        return authorizer.explain(userId, docId);
    }
}
