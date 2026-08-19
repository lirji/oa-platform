package com.lrj.oa.org.api.dto;

import java.util.List;

/** 组织树节点（含子节点），供前端一次性渲染或懒加载。 */
public record OrgTreeNodeView(
        Long id, Long parentId, String code, String name, String type,
        String path, int depth, int sortOrder, String status,
        String leaderUserId,
        /**
         * 成员数。★ 为 null 表示【未统计】而不是 0 —— 树接口默认不带，需要时另查。
         * 之前用 -1 当哨兵，前端直接渲染会显示一个看起来像真实数据的 -1。
         */
        Integer memberCount,
        List<OrgTreeNodeView> children
) {}
