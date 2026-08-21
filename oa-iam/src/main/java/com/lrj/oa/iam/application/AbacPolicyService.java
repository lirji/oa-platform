package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.PermissionCondition;
import com.lrj.oa.iam.infrastructure.mapper.PermissionConditionMapper;
import com.lrj.oa.iam.infrastructure.mapper.PermissionMapper;
import com.lrj.oa.iam.infrastructure.mapper.RoleMapper;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AbacPolicyService {
    private final PermissionConditionMapper mapper;
    private final PermissionMapper permissions;
    private final RoleMapper roles;
    private final AbacEvaluator evaluator;
    private final IamInvalidationService invalidation;

    public AbacPolicyService(PermissionConditionMapper mapper, PermissionMapper permissions, RoleMapper roles,
                             AbacEvaluator evaluator, IamInvalidationService invalidation) {
        this.mapper = mapper;
        this.permissions = permissions;
        this.roles = roles;
        this.evaluator = evaluator;
        this.invalidation = invalidation;
    }

    public List<PermissionCondition> list(Long roleId, Long permissionId) {
        return mapper.selectPolicies(TenantContext.get(), roleId, permissionId);
    }

    @Transactional
    public long create(IamCommands.AbacCondition cmd) {
        validateTarget(cmd.roleId(), cmd.permissionId());
        validateConditionLimit(cmd.roleId(), cmd.permissionId(), false);
        evaluator.validate(cmd.expression());
        PermissionCondition condition = new PermissionCondition();
        condition.setTenantId(TenantContext.get());
        condition.setRoleId(cmd.roleId());
        condition.setPermissionId(cmd.permissionId());
        condition.setExpression(cmd.expression().trim());
        condition.setDescription(blankToNull(cmd.description()));
        condition.setEnabled(cmd.enabled() == null || cmd.enabled());
        condition.setCreatedBy(currentUser());
        condition.setCreatedAt(OffsetDateTime.now());
        condition.setUpdatedBy(currentUser());
        condition.setUpdatedAt(OffsetDateTime.now());
        mapper.insert(condition);
        if (Boolean.TRUE.equals(condition.getEnabled())) invalidation.all("abac-create#" + condition.getId());
        return condition.getId();
    }

    @Transactional
    public void update(long id, IamCommands.AbacCondition cmd) {
        PermissionCondition before = require(id);
        validateTarget(cmd.roleId(), cmd.permissionId());
        boolean sameTarget = before.getRoleId().equals(cmd.roleId())
                && before.getPermissionId().equals(cmd.permissionId());
        validateConditionLimit(cmd.roleId(), cmd.permissionId(), sameTarget);
        evaluator.validate(cmd.expression());
        mapper.updatePolicy(TenantContext.get(), id, cmd.roleId(), cmd.permissionId(), cmd.expression().trim(),
                blankToNull(cmd.description()), currentUser());
        boolean requestedEnabled = cmd.enabled() == null ? Boolean.TRUE.equals(before.getEnabled()) : cmd.enabled();
        mapper.setEnabled(TenantContext.get(), id, requestedEnabled, currentUser());
        if (Boolean.TRUE.equals(before.getEnabled()) || requestedEnabled) invalidation.all("abac-update#" + id);
    }

    @Transactional
    public void setEnabled(long id, boolean enabled) {
        require(id);
        if (mapper.setEnabled(TenantContext.get(), id, enabled, currentUser()) > 0) {
            invalidation.all("abac-enabled#" + id);
        }
    }

    @Transactional
    public void delete(long id) {
        PermissionCondition before = require(id);
        mapper.softDelete(TenantContext.get(), id, currentUser());
        if (Boolean.TRUE.equals(before.getEnabled())) invalidation.all("abac-delete#" + id);
    }

    public void validateExpression(String expression) { evaluator.validate(expression); }

    private PermissionCondition require(long id) {
        PermissionCondition condition = mapper.selectLiveById(TenantContext.get(), id);
        if (condition == null) throw BusinessException.of(ResultCode.NOT_FOUND, "ABAC 条件不存在: " + id);
        return condition;
    }

    private void validateTarget(long roleId, long permissionId) {
        long tenantId = TenantContext.get();
        if (roles.selectTenantById(tenantId, roleId) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "角色不存在: " + roleId);
        }
        if (permissions.selectById(permissionId) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "权限点不存在: " + permissionId);
        }
        if (!roles.roleIncludesPermission(tenantId, roleId, permissionId)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "该角色（含继承）不包含目标权限点");
        }
    }

    private void validateConditionLimit(long roleId, long permissionId, boolean existingAtTarget) {
        int count = mapper.countLiveForTarget(TenantContext.get(), roleId, permissionId);
        if (count >= 32 && !existingAtTarget) {
            throw BusinessException.of(ResultCode.CONFLICT, "同一角色权限点最多配置 32 条 ABAC 条件");
        }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String currentUser() {
        var ctx = UserContextHolder.peek();
        return ctx == null ? "system" : ctx.userId();
    }
}
