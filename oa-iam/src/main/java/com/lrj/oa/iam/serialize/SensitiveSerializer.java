package com.lrj.oa.iam.serialize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.security.annotation.Sensitive;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import com.lrj.oa.security.model.SensitiveType;

import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;

/**
 * 列级数据权限：序列化时按当前用户是否持有对应权限点，决定明文还是脱敏。
 *
 * <p>放在序列化层而不是业务层，是因为<b>所有数据出网都必经序列化</b>，
 * 而 VO 组装点有几十上百处，写在那里必漏。
 */
public class SensitiveSerializer extends JsonSerializer<Object> {

    private final Sensitive annotation;
    private final ObjectProvider<PermissionEngine> engineProvider;
    private volatile PermissionEngine engine;

    public SensitiveSerializer(Sensitive annotation, ObjectProvider<PermissionEngine> engineProvider) {
        this.annotation = annotation;
        this.engineProvider = engineProvider;
    }

    /** 惰性解析：见 SensitiveModule 的说明，构造期拿会形成 Bean 循环。 */
    private PermissionEngine engine() {
        PermissionEngine e = engine;
        if (e == null) { e = engineProvider.getObject(); engine = e; }
        return e;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) { gen.writeNull(); return; }
        String raw = String.valueOf(value);
        gen.writeString(allowed() ? raw : mask(raw, annotation.type()));
    }

    private boolean allowed() {
        String perm = annotation.perm();
        if (perm == null || perm.isBlank()) return false;      // 未指定权限点 = 对所有人脱敏
        UserContext ctx = UserContextHolder.peek();
        if (ctx == null) return false;                          // 无身份一律脱敏
        return engine().has(ctx.userId(), perm);
    }

    static String mask(String v, SensitiveType type) {
        if (v.isEmpty()) return v;
        return switch (type) {
            case MOBILE  -> v.length() < 7 ? "***" : v.substring(0, 3) + "****" + v.substring(v.length() - 4);
            case ID_CARD -> v.length() < 8 ? "***" : v.substring(0, 3) + "***********" + v.substring(v.length() - 4);
            case EMAIL   -> maskEmail(v);
            case BANK_CARD -> v.length() < 4 ? "***" : "**** **** **** " + v.substring(v.length() - 4);
            case NAME    -> v.length() <= 1 ? v : v.charAt(0) + "*".repeat(v.length() - 1);
            case ADDRESS -> v.length() <= 6 ? "***" : v.substring(0, 6) + "***";
            case AMOUNT  -> "***";
        };
    }

    private static String maskEmail(String v) {
        int at = v.indexOf('@');
        if (at <= 0) return "***";
        String name = v.substring(0, at);
        String head = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return head + "***" + v.substring(at);
    }
}
