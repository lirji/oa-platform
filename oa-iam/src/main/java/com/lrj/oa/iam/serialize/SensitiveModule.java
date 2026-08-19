package com.lrj.oa.iam.serialize;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.security.annotation.Sensitive;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 把 {@link Sensitive} 接进 Jackson。注册一次，全应用所有 VO 自动生效。
 *
 * <p><b>这里必须用 {@link ObjectProvider} 惰性拿判权引擎</b>：本 Bean 是 ObjectMapper 的
 * 构造参与者，而判权引擎链路上又需要 ObjectMapper（组织树缓存 → 失效总线 → ObjectMapper）。
 * 直接注入会形成 Bean 循环依赖，启动即失败。惰性查找把这条边推迟到第一次序列化时，环自然断开。
 */
@Configuration
public class SensitiveModule {

    @Bean
    public SimpleModule oaSensitiveModule(ObjectProvider<PermissionEngine> engineProvider) {
        SimpleModule module = new SimpleModule("oa-sensitive");
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                             BeanDescription beanDesc,
                                                             List<BeanPropertyWriter> properties) {
                for (BeanPropertyWriter w : properties) {
                    Sensitive ann = w.getAnnotation(Sensitive.class);
                    if (ann != null) w.assignSerializer(new SensitiveSerializer(ann, engineProvider));
                }
                return properties;
            }
        });
        return module;
    }
}
