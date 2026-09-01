package com.lrj.oa.iam.application;

import com.lrj.oa.iam.domain.GrantRecord;
import com.lrj.oa.iam.domain.GrantType;
import com.lrj.oa.iam.domain.SubjectType;
import com.lrj.oa.iam.infrastructure.mapper.GrantMapper;
import com.lrj.oa.iam.infrastructure.mapper.RoleMapper;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.iam.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 初始管理员引导。
 *
 * <p>先有权限系统还是先有管理员，是个鸡生蛋问题：没人有 {@code oa:iam:grant}，
 * 谁也授不了第一个权。这里用一个显式配置项破局，而<b>不是</b>在判权切面里开一个
 * "某某 userId 直接放行"的后门 —— 后门一旦存在就永远拆不掉，而这条只是往
 * {@code grant_record} 里补一行真实授权，之后走的完全是正常路径。
 *
 * <p>只在该用户<b>一条 SUPER_ADMIN 授权都没有</b>时才补，重启不会重复插入。
 * 生产环境初始化完成后应把这个配置清空。
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final GrantMapper grantMapper;
    private final RoleMapper roleMapper;
    private final IamInvalidationService invalidation;
    private final String bootstrapUserId;

    public BootstrapAdminInitializer(GrantMapper grantMapper, RoleMapper roleMapper,
                                     IamInvalidationService invalidation,
                                     @Value("${oa.iam.bootstrap-admin-user-id:}") String bootstrapUserId) {
        this.grantMapper = grantMapper;
        this.roleMapper = roleMapper;
        this.invalidation = invalidation;
        this.bootstrapUserId = bootstrapUserId;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapUserId == null || bootstrapUserId.isBlank()) return;

        Role superAdmin = roleMapper.selectByCode(TenantContext.DEFAULT_TENANT_ID, "SUPER_ADMIN");
        if (superAdmin == null) {
            log.error("找不到 SUPER_ADMIN 角色，初始管理员引导跳过（V10 迁移是否执行过？）");
            return;
        }

        boolean already = grantMapper.selectBySubject(TenantContext.DEFAULT_TENANT_ID,
                        SubjectType.USER.name(), bootstrapUserId).stream()
                .anyMatch(g -> superAdmin.getId().equals(g.getRoleId()));
        if (already) {
            log.info("初始管理员 {} 已持有 SUPER_ADMIN，无需引导", bootstrapUserId);
            return;
        }

        GrantRecord g = new GrantRecord();
        g.setTenantId(TenantContext.DEFAULT_TENANT_ID);
        g.setSubjectType(SubjectType.USER.name());
        g.setSubjectId(bootstrapUserId);
        g.setRoleId(superAdmin.getId());
        g.setScopeType("ALL");
        g.setIncludeDescendants(false);
        g.setGrantType(GrantType.PERMANENT.name());
        g.setValidFrom(OffsetDateTime.now());
        g.setSource("SYNC");
        g.setReason("初始管理员引导 oa.iam.bootstrap-admin-user-id");
        g.setGrantedBy("system");
        g.setGrantedAt(OffsetDateTime.now());
        grantMapper.insert(g);
        // ApplicationRunner starts after the web server; after-commit invalidation also removes any early empty snapshot.
        invalidation.all("bootstrap-admin#" + bootstrapUserId);

        log.warn("★ 已为初始管理员 {} 授予 SUPER_ADMIN（授权 id={}）。初始化完成后请清空该配置。",
                bootstrapUserId, g.getId());
    }
}
