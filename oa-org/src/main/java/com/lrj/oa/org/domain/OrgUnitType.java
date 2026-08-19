package com.lrj.oa.org.domain;

/**
 * 组织单元类型。
 *
 * <p><b>它只是标签，不是结构</b>（ADR-0006）。层级深度与嵌套关系由 parent_id / closure / path 表达，
 * 允许任意组合 —— 现实中"事业部下面直接挂组"是常态，把层级写死在表结构里的设计撑不过第一次组织调整。
 */
public enum OrgUnitType {
    GROUP,     // 集团
    COMPANY,   // 公司（法人）
    BU,        // 事业部
    CENTER,    // 中心
    DEPT,      // 部门
    TEAM,      // 团队
    SQUAD,     // 组
    VIRTUAL    // 虚拟组（项目组/委员会，不进人事口径）
}
