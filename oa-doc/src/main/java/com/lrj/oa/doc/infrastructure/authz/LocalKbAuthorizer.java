package com.lrj.oa.doc.infrastructure.authz;

import com.lrj.oa.doc.api.dto.DocDtos;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内置知识库授权（{@code oa.authz.spicedb.enabled=false}，默认）。
 *
 * <p>判定顺序：本人是 owner → 有 ALL 共享 → 有 USER 共享 → 有覆盖本人所在组织的 ORG 共享。
 *
 * <p>ORG 共享用 <b>org_path 前缀匹配</b>：授权给「研发中心」，其下所有子部门的人都能看。
 * 这与数据权限用的是同一套路数 —— 绝不展开成 id 列表，那在万人级下是个几千元素的 IN。
 */
@Component
@ConditionalOnProperty(name = "oa.authz.spicedb.enabled", havingValue = "false", matchIfMissing = true)
public class LocalKbAuthorizer implements KbAuthorizer {

    private final JdbcTemplate jdbc;

    public LocalKbAuthorizer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public String name() { return "LOCAL(kb_share)"; }

    @Override
    public boolean canAccess(String userId, String resourceType, long resourceId, String level) {
        return decide(userId, resourceType, resourceId, level).allowed();
    }

    @Override
    public DocDtos.KbAccessExplain explain(String userId, long docId) {
        Decision d = decide(userId, "DOC", docId, "READ");
        return new DocDtos.KbAccessExplain(docId, userId, d.allowed(), d.level(), name(), d.reason());
    }

    private record Decision(boolean allowed, String level, String reason) {}

    private Decision decide(String userId, String resourceType, long resourceId, String wantLevel) {
        boolean needWrite = "WRITE".equalsIgnoreCase(wantLevel);

        if ("DOC".equals(resourceType)) {
            String owner = jdbc.query("SELECT owner_id FROM oa_doc.kb_doc WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null, resourceId);
            if (owner == null) return new Decision(false, null, "文档不存在");
            if (owner.equals(userId)) return new Decision(true, "WRITE", "本人是文档 owner");
        }

        // 当前用户所在组织的 path。ORG 共享判定要用它做【被包含】关系：
        // 授权路径是我的路径的前缀 ⇒ 我在那棵子树里。
        UserContext ctx = UserContextHolder.peek();
        String myPath = ctx == null ? null : ctx.primaryOrgPath();

        List<String> levels = jdbc.query("""
                SELECT level FROM oa_doc.kb_share
                 WHERE resource_type = ? AND resource_id = ?
                   AND ( subject_type = 'ALL'
                      OR (subject_type = 'USER' AND subject_id = ?)
                      OR (subject_type = 'ORG'  AND ? IS NOT NULL
                          AND ? LIKE subject_org_path || '%') )
                """, (rs, i) -> rs.getString(1), resourceType, resourceId, userId, myPath, myPath);

        if (levels.isEmpty()) {
            return new Decision(false, null, "没有任何共享覆盖该用户（owner/ALL/USER/ORG 均不匹配）");
        }
        boolean hasWrite = levels.contains("WRITE");
        if (needWrite && !hasWrite) {
            return new Decision(false, "READ", "只有只读共享，无写权限");
        }
        return new Decision(true, hasWrite ? "WRITE" : "READ",
                "命中共享记录 " + levels + "（ORG 共享按 org_path 前缀继承）");
    }
}
