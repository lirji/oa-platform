package com.lrj.oa.org.api.dto;

/** 组织单元视图。跨模块与前端都用它，不暴露实体。 */
public record OrgUnitView(
        Long id, Long parentId, String code, String name, String shortName,
        String type, String path, int depth, int sortOrder,
        String leaderUserId, String deputyLeaderUserId, String status
) {}
