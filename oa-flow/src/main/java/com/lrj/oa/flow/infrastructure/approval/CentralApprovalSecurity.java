package com.lrj.oa.flow.infrastructure.approval;

import com.lrj.authz.protocol.ApprovalSignature;
import com.lrj.oa.flow.infrastructure.mapper.CentralApprovalMapper;
import com.lrj.oa.security.context.ServiceIdentity;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** 独立内部命名空间始终被保护，禁用或DEV模式也不回退员工/匿名认证链。 */
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(CentralApprovalProperties.class)
public class CentralApprovalSecurity {
    public static final String BODY=CentralApprovalSecurity.class.getName()+".body";

    /** 先验原始字节签名与nonce，再向控制器提供同一份字节。 */
    @Bean @Order(0)
    SecurityFilterChain centralApprovalFilterChain(HttpSecurity http,CentralApprovalProperties config,CentralApprovalMapper mapper) throws Exception {
        http.securityMatcher("/internal/iam-approval/**").csrf(c->c.disable()).requestCache(c->c.disable())
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(new SignedFilter(config,mapper),SecurityContextHolderFilter.class)
                .authorizeHttpRequests(a->a.requestMatchers("/internal/iam-approval/v1/start","/internal/iam-approval/v1/lookup").authenticated().anyRequest().denyAll());
        return http.build();
    }

    static final class SignedFilter extends OncePerRequestFilter {
        private final CentralApprovalProperties config;
        private final CentralApprovalMapper mapper;
        SignedFilter(CentralApprovalProperties config,CentralApprovalMapper mapper) { this.config=config;this.mapper=mapper; }
        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
            if(!config.enabled()) { response.setStatus(404);return; }
            if(!request.getMethod().equals("POST") || request.getQueryString()!=null) { response.setStatus(405);return; }
            boolean local=config.allowLoopbackHttp() && loopback(request.getRemoteAddr()) && loopback(request.getLocalAddr());
            if(!request.isSecure() && !local) { response.setStatus(403);return; }
            byte[] body=request.getInputStream().readNBytes(ApprovalSignature.MAX_BODY+1);
            var headers=Collections.list(request.getHeaders(ApprovalSignature.HEADER));
            String nonce;
            try {
                if(headers.size()!=1) throw new IllegalArgumentException();
                nonce=ApprovalSignature.verify(config.inboundKey(),"auth-platform",config.environment(),request.getRequestURI(),body,headers.getFirst(),Instant.now());
            } catch(IllegalArgumentException invalid) { response.setStatus(401);return; }
            if(mapper.nonce(nonce)!=1) { response.setStatus(409);return; }
            request.setAttribute(BODY,body);
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new ServiceIdentity("auth-platform",config.oaTenantId()),null,List.of()));
            SecurityContextHolder.setContext(context);
            try { chain.doFilter(request,response); }
            finally { SecurityContextHolder.clearContext(); }
        }
        private static boolean loopback(String ip) { return Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(ip); }
    }
}
