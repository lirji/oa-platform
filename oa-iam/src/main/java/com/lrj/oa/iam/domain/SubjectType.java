package com.lrj.oa.iam.domain;

/**
 * 授权主体类型。
 *
 * <p>{@code ORG_UNIT} 是"部门权限继承"的载体：授权给部门，配合 {@code include_descendants}
 * 决定是只惠及本部门，还是连同所有子部门全员。
 *
 * <p>{@code USER_GROUP} 由 OA 内部 {@code user_group/user_group_member} 解析，
 * 成员时间窗参与权限快照的有效期边界。
 */
public enum SubjectType { USER, ORG_UNIT, POSITION, USER_GROUP }
