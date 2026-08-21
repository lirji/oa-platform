package com.lrj.oa.security.annotation;

import java.lang.annotation.*;

/**
 * 显式声明系统级数据权限旁路。
 *
 * <p>只允许后台任务、权限真值构建和基础设施维护使用。普通请求服务使用旁路应被架构测试拒绝；
 * reason 与 tables 都进入审计，不能作为“先绕过再说”的逃生开关。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScopeBypass {
    String reason();
    String[] tables();
}
