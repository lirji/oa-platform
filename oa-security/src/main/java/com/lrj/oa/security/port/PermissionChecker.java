package com.lrj.oa.security.port;

import com.lrj.oa.security.model.DataScopeRule;

import java.util.Collection;

/**
 * 判权端口。<b>oa-security 只定义端口，不含实现</b> —— 实现在 oa-iam。
 *
 * <p>这样 oa-security 不依赖 oa-iam，任何模块都能安全地依赖注解与上下文，
 * 而不会把权限域的实现细节拖进来（六边形：注解是驱动端口，oa-iam 是驱动适配器）。
 *
 * <p>所有方法都必须是<b>本地内存判定</b>：零 DB、零远程调用，目标 P99 &lt; 1ms。
 */
public interface PermissionChecker {

    /** 当前用户是否持有该权限点。 */
    boolean has(String userId, String permCode);

    boolean hasAll(String userId, Collection<String> permCodes);

    boolean hasAny(String userId, Collection<String> permCodes);

    /**
     * 该权限点是否已被一条<b>活跃的 JIT 临时提权</b>覆盖。
     * 用于 {@code @RequiresPerm(elevation = true)} 的高危操作。
     */
    boolean isElevated(String userId, String permCode);

    /** 取用户通过指定权限点获得的数据范围规则；未知或未持有权限时返回 NONE。 */
    DataScopeRule dataScopeForPermission(String userId, String permissionCode);

    /** 我正在代理谁（委托关系）。待办查询用它扩展 assignee 集合。 */
    Collection<String> delegators(String userId);
}
