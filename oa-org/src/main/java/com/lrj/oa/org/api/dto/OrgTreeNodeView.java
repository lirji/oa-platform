package com.lrj.oa.org.api.dto;

import java.util.List;

/** 组织树节点（含子节点），供前端一次性渲染或懒加载。 */
public record OrgTreeNodeView(
        Long id, Long parentId, String code, String name, String type,
        String path, int depth, int sortOrder, String status,
        String leaderUserId, int memberCount, List<OrgTreeNodeView> children
) {}
