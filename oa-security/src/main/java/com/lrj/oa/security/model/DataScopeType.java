package com.lrj.oa.security.model;

/** 数据权限范围类型（对应 grant_record.scope_type）。 */
public enum DataScopeType {
    /** 全部数据，不加任何条件。 */
    ALL,
    /** 本组织 + 所有子组织 —— 落到 SQL 是 org_path 前缀匹配。 */
    ORG_AND_SUB,
    /** 仅本组织。 */
    ORG,
    /** 仅本人创建的数据。 */
    SELF,
    /** 自定义组织集合（grant_record.scope_org_ids）。 */
    CUSTOM,
    /** 无任何数据权限（拒绝一切）。作为合并的单位元，安全默认值。 */
    NONE;

    /** 范围宽度，用于多条授权合并时取最宽。 */
    public int width() {
        return switch (this) {
            case ALL -> 100;
            case CUSTOM -> 60;
            case ORG_AND_SUB -> 50;
            case ORG -> 30;
            case SELF -> 10;
            case NONE -> 0;
        };
    }
}
