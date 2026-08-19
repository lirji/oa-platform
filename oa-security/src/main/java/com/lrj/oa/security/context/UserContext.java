package com.lrj.oa.security.context;

/**
 * 当前请求的用户上下文。
 *
 * <p><b>userId 是 Casdoor 的 {@code sub}（UUID 字符串），全系统唯一主体标识。</b>
 * 绝不要用自增 Long 当 subject —— his-platform 踩过这个错配的坑
 * （his 用 {@code Long userId}，与 auth 规范的 {@code sub} 对不上）。
 *
 * @param userId       Casdoor sub（UUID）
 * @param username     Casdoor name claim（可读用户名）
 * @param employeeId   OA 内部员工 id（可空：账号存在但未入职）
 * @param primaryOrgId 主岗所在组织 id
 * @param primaryOrgPath 主岗组织物化路径，如 '/1/23/456/'，数据权限直接用它
 * @param tenantId     租户 id（本轮恒为 1）
 */
public record UserContext(
        String userId,
        String username,
        Long employeeId,
        Long primaryOrgId,
        String primaryOrgPath,
        long tenantId
) {
    public boolean isEmployee() { return employeeId != null; }
}
