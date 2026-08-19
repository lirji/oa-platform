package com.lrj.oa.iam.aspect;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class IamWebMvcConfig implements WebMvcConfigurer {

    private final UnannotatedHandlerGuard guard;

    public IamWebMvcConfig(UnannotatedHandlerGuard guard) { this.guard = guard; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard).addPathPatterns("/**");
    }
}
