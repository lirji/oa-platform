package com.lrj.oa.iam.domain;

/**
 * 授权主体类型。
 *
 * <p>{@code ORG_UNIT} 是"部门权限继承"的载体：授权给部门，配合 {@code include_descendants}
 * 决定是只惠及本部门，还是连同所有子部门全员。
 *
 * <p>{@code USER_GROUP} 已在表里预留，但 Phase 2 <b>不解析</b> —— 它需要一张动态人群表，
 * 属于 Phase 3 的范围。解析器遇到它会跳过并告警，而不是静默当成无权限。
 */
public enum SubjectType { USER, ORG_UNIT, POSITION, USER_GROUP }
