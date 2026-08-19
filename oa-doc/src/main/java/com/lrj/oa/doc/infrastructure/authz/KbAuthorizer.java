package com.lrj.oa.doc.infrastructure.authz;

import com.lrj.oa.doc.api.dto.DocDtos;

/**
 * 知识库的对象级授权端口。
 *
 * <p>ADR 决定：知识库/网盘这类<b>任意对象 ACL</b> 是 auth-platform SpiceDB 的强项，
 * 可选接入；但组织 RBAC 仍走本地 PermissionEngine（远程 check 扛不住页面权限的量）。
 * 于是这里有两个实现，由 {@code oa.authz.spicedb.enabled} 选择，<b>默认 false</b>。
 *
 * <p>两条路径必须都能<b>独立</b>工作 —— 否则"关掉开关回退"就只是句口号。
 * 内置实现基于 {@code oa_doc.kb_share} 表，SpiceDB 实现基于 knowledge.zed 的关系图。
 */
public interface KbAuthorizer {

    /** 实现名，落在解释结果里，让人一眼看出当前走的是哪条路径。 */
    String name();

    /** 能否以 level（READ/WRITE）访问该资源。 */
    boolean canAccess(String userId, String resourceType, long resourceId, String level);

    /** 为什么 —— 权限系统必须能解释自己，否则出问题时只能靠猜。 */
    DocDtos.KbAccessExplain explain(String userId, long docId);
}
