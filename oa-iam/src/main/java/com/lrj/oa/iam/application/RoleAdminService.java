package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.RoleAdminDtos.RoleDetail;
import com.lrj.oa.iam.api.dto.RoleAdminDtos.RoleSummary;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.Role;
import com.lrj.oa.iam.infrastructure.mapper.RoleMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class RoleAdminService {
    private static final int MAX_ROLE_RELATIONS = 1_000;
    private static final List<String> SCOPES = List.of("ALL", "ORG_AND_SUB", "ORG", "SELF", "CUSTOM", "NONE");

    private final RoleMapper mapper;
    private final IamInvalidationService invalidation;

    public RoleAdminService(RoleMapper mapper, IamInvalidationService invalidation) {
        this.mapper = mapper;
        this.invalidation = invalidation;
    }

    public List<RoleSummary> list(String status, String keyword) {
        long tenantId = TenantContext.get();
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedKeyword = blankToNull(keyword);
        return mapper.selectAdminRoleSummaries(tenantId, normalizedStatus, normalizedKeyword);
    }

    public RoleDetail detail(long id) {
        long tenantId = TenantContext.get();
        return detail(tenantId, require(tenantId, id));
    }

    @Transactional
    public long create(IamCommands.CreateRole cmd) {
        long tenantId = TenantContext.get();
        mapper.lockTenantRoleGraph(tenantId);
        String code = normalizeCode(cmd.code());
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setName(normalizeName(cmd.name()));
        role.setType("CUSTOM");
        role.setDefaultScope(normalizeScope(cmd.defaultScope()));
        role.setStatus("ACTIVE");
        role.setBuiltin(false);
        role.setVersion(0);
        role.setRemark(blankToNull(cmd.remark()));
        try {
            mapper.insert(role);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.of(ResultCode.CONFLICT, "角色 code 已存在: " + code);
        }
        replacePermissionsUnchecked(tenantId, role.getId(), unique(cmd.permissionIds(), "权限"));
        replaceInheritanceUnchecked(tenantId, role.getId(), unique(cmd.inheritedRoleIds(), "继承角色"));
        rebuildClosure(tenantId);
        invalidation.all("role-create#" + role.getId());
        return role.getId();
    }

    @Transactional
    public void update(long id, IamCommands.UpdateRole cmd) {
        long tenantId = TenantContext.get();
        require(tenantId, id);
        if (mapper.updateRole(tenantId, id, normalizeName(cmd.name()), normalizeScope(cmd.defaultScope()),
                blankToNull(cmd.remark()), cmd.version()) == 0) {
            conflict();
        }
        invalidation.all("role-update#" + id);
    }

    @Transactional
    public long copy(long id, IamCommands.CopyRole cmd) {
        long tenantId = TenantContext.get();
        mapper.lockTenantRoleGraph(tenantId);
        Role source = require(tenantId, id);
        if ("DELETED".equals(source.getStatus())) notFound(id);
        Role copy = new Role();
        copy.setTenantId(tenantId);
        copy.setCode(normalizeCode(cmd.code()));
        copy.setName(normalizeName(cmd.name()));
        copy.setType("CUSTOM");
        copy.setDefaultScope(source.getDefaultScope());
        copy.setStatus("ACTIVE");
        copy.setBuiltin(false);
        copy.setVersion(0);
        copy.setRemark(source.getRemark());
        try {
            mapper.insert(copy);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.of(ResultCode.CONFLICT, "角色 code 已存在: " + copy.getCode());
        }
        replacePermissionsUnchecked(tenantId, copy.getId(), mapper.selectDirectPermissionIds(id));
        replaceInheritanceUnchecked(tenantId, copy.getId(), mapper.selectInheritedRoleIds(tenantId, id));
        rebuildClosure(tenantId);
        invalidation.all("role-copy#" + id + "->" + copy.getId());
        return copy.getId();
    }

    @Transactional
    public void setEnabled(long id, IamCommands.SetRoleEnabled cmd) {
        long tenantId = TenantContext.get();
        mapper.lockTenantRoleGraph(tenantId);
        Role role = require(tenantId, id);
        if (Boolean.TRUE.equals(role.getBuiltin()) && !cmd.enabled()) {
            throw BusinessException.of(ResultCode.CONFLICT, "内置角色不能停用");
        }
        if (mapper.updateStatus(tenantId, id, cmd.enabled() ? "ACTIVE" : "DISABLED", cmd.version()) == 0) {
            conflict();
        }
        rebuildClosure(tenantId);
        invalidation.all("role-status#" + id + "=" + cmd.enabled());
    }

    @Transactional
    public void delete(long id, int version) {
        long tenantId = TenantContext.get();
        mapper.lockTenantRoleGraph(tenantId);
        Role role = require(tenantId, id);
        if (Boolean.TRUE.equals(role.getBuiltin())) {
            throw BusinessException.of(ResultCode.CONFLICT, "内置角色不能删除");
        }
        if (mapper.countGrantReferences(tenantId, id) > 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "角色已有授权记录，请先撤销或保留该角色");
        }
        mapper.deleteDirectPermissions(id);
        mapper.deleteInheritanceEdges(tenantId, id);
        // 同时清理其它角色指向它的直接边，避免软删除节点继续参与闭包。
        mapper.deleteIncomingInheritanceEdges(tenantId, id);
        if (mapper.softDelete(tenantId, id, version) == 0) conflict();
        rebuildClosure(tenantId);
        invalidation.all("role-delete#" + id);
    }

    @Transactional
    public void replacePermissions(long id, IamCommands.ReplaceRolePermissions cmd) {
        long tenantId = TenantContext.get();
        Role role = require(tenantId, id);
        protectSuperAdminMatrix(role);
        List<Long> ids = unique(cmd.permissionIds(), "权限");
        if (mapper.touchVersion(tenantId, id, cmd.version()) == 0) conflict();
        replacePermissionsUnchecked(tenantId, id, ids);
        invalidation.all("role-permissions#" + id);
    }

    @Transactional
    public void replaceInheritance(long id, IamCommands.ReplaceRoleInheritance cmd) {
        long tenantId = TenantContext.get();
        mapper.lockTenantRoleGraph(tenantId);
        Role role = require(tenantId, id);
        protectSuperAdminMatrix(role);
        List<Long> ids = unique(cmd.inheritedRoleIds(), "继承角色");
        if (ids.contains(id)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "角色不能继承自己");
        }
        validateTenantRoles(tenantId, ids);
        if (mapper.touchVersion(tenantId, id, cmd.version()) == 0) conflict();
        mapper.deleteInheritanceEdges(tenantId, id);
        if (!ids.isEmpty()) mapper.insertInheritanceEdges(tenantId, id, ids);
        if (mapper.countInheritanceCycles(tenantId) > 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "角色继承不能形成环路");
        }
        rebuildClosure(tenantId);
        invalidation.all("role-inheritance#" + id);
    }

    private RoleDetail detail(long tenantId, Role role) {
        return new RoleDetail(role.getId(), role.getCode(), role.getName(), role.getType(),
                role.getDefaultScope(), role.getStatus(), Boolean.TRUE.equals(role.getBuiltin()),
                role.getVersion(), role.getRemark(), mapper.selectDirectPermissionIds(role.getId()),
                mapper.selectEffectivePermissionIds(role.getId()),
                mapper.selectInheritedRoleIds(tenantId, role.getId()),
                mapper.countGrantReferences(tenantId, role.getId()));
    }

    private void replacePermissionsUnchecked(long tenantId, long roleId, List<Long> ids) {
        validatePermissions(ids);
        mapper.deleteDirectPermissions(roleId);
        if (!ids.isEmpty()) mapper.insertDirectPermissions(roleId, ids);
    }

    private void replaceInheritanceUnchecked(long tenantId, long roleId, List<Long> ids) {
        if (ids.contains(roleId)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "角色不能继承自己");
        }
        validateTenantRoles(tenantId, ids);
        mapper.deleteInheritanceEdges(tenantId, roleId);
        if (!ids.isEmpty()) mapper.insertInheritanceEdges(tenantId, roleId, ids);
        if (mapper.countInheritanceCycles(tenantId) > 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "角色继承不能形成环路");
        }
    }

    private void rebuildClosure(long tenantId) {
        mapper.deleteTenantClosure(tenantId);
        mapper.insertTenantSelfClosure(tenantId);
        mapper.insertTenantTransitiveClosure(tenantId);
    }

    private Role require(long tenantId, long id) {
        Role role = mapper.selectTenantById(tenantId, id);
        if (role == null || "DELETED".equals(role.getStatus())) notFound(id);
        return role;
    }

    private void validatePermissions(List<Long> ids) {
        if (!ids.isEmpty() && mapper.countActivePermissions(ids) != ids.size()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "权限点不存在或已禁用");
        }
    }

    private void validateTenantRoles(long tenantId, List<Long> ids) {
        if (!ids.isEmpty() && mapper.countTenantRoles(tenantId, ids) != ids.size()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "继承角色不存在、已删除或不属于当前租户");
        }
    }

    private static List<Long> unique(List<Long> input, String label) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>(input == null ? List.of() : input);
        if (ids.size() > MAX_ROLE_RELATIONS) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, label + "单次最多 1000 项");
        }
        return List.copyOf(ids);
    }

    private static String normalizeCode(String code) {
        String value = code.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_-]{1,63}")) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "角色 code 需为 2~64 位大写字母/数字/_/-");
        }
        return value;
    }

    private static String normalizeName(String name) {
        String value = name.trim();
        if (value.length() > 128) throw BusinessException.of(ResultCode.BAD_REQUEST, "角色名称不能超过 128 字符");
        return value;
    }

    private static String normalizeScope(String scope) {
        String value = scope.trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(value)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "非法默认数据范围: " + scope);
        }
        return value;
    }

    private static String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) return null;
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "DISABLED", "DELETED").contains(value)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "非法角色状态: " + status);
        }
        return value;
    }

    private static void protectSuperAdminMatrix(Role role) {
        if (Boolean.TRUE.equals(role.getBuiltin()) && "SUPER_ADMIN".equals(role.getCode())) {
            throw BusinessException.of(ResultCode.CONFLICT, "超级管理员的权限和继承关系不能在线修改，避免管理入口锁死");
        }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void conflict() { throw BusinessException.of(ResultCode.CONFLICT, "角色已被其他管理员修改，请刷新后重试"); }
    private static void notFound(long id) { throw BusinessException.of(ResultCode.NOT_FOUND, "角色不存在: " + id); }
}
