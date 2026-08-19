package com.lrj.oa.security.annotation;

import com.lrj.oa.security.model.SensitiveType;

import java.lang.annotation.*;

/**
 * 敏感字段（列级数据权限）。由 Jackson 的 BeanSerializerModifier 在<b>序列化层</b>统一脱敏。
 *
 * <p>为什么在序列化层而不是业务层：业务层每个 VO 组装点都写一遍必然会漏，
 * 而所有数据出网都必经序列化 —— 这是唯一的收口点。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sensitive {

    SensitiveType type();

    /** 持有该权限点则明文返回，否则脱敏。留空表示对所有人脱敏。 */
    String perm() default "";
}
