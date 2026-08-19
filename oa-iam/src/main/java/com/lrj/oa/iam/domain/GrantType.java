package com.lrj.oa.iam.domain;

/**
 * 授权形态。三者语义不同，不能混为一谈：
 * <ul>
 *   <li>{@code PERMANENT} 常规授权；</li>
 *   <li>{@code TEMPORARY} 定时角色 / JIT 提权 —— 靠 {@code valid_to} 到点自动失效，
 *       同时也是 {@code @RequiresPerm(elevation = true)} 的放行凭据；</li>
 *   <li>{@code DELEGATED} 由委托关系派生，<b>不叠加权限</b>，只让代理人能办委托人的待办。</li>
 * </ul>
 */
public enum GrantType { PERMANENT, TEMPORARY, DELEGATED }
