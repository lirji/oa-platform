package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.UserGroup;
import com.lrj.oa.iam.domain.UserGroupMember;
import com.lrj.oa.iam.infrastructure.mapper.UserGroupMapper;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class UserGroupService {
    private final UserGroupMapper mapper;
    private final OrgQueryApi org;
    private final IamInvalidationService invalidation;

    public UserGroupService(UserGroupMapper mapper, OrgQueryApi org, IamInvalidationService invalidation) {
        this.mapper = mapper;
        this.org = org;
        this.invalidation = invalidation;
    }

    public List<UserGroup> list(String status) {
        String normalized = status == null || status.isBlank() ? null : normalizeStatus(status);
        return mapper.selectGroups(TenantContext.get(), normalized);
    }

    public UserGroup require(long id) {
        UserGroup group = mapper.selectTenantGroup(TenantContext.get(), id);
        if (group == null) throw BusinessException.of(ResultCode.NOT_FOUND, "用户组不存在: " + id);
        return group;
    }

    @Transactional
    public long create(IamCommands.CreateGroup cmd) {
        UserGroup group = new UserGroup();
        group.setTenantId(TenantContext.get());
        group.setCode(normalizeCode(cmd.code()));
        group.setName(cmd.name().trim());
        group.setDescription(blankToNull(cmd.description()));
        group.setStatus("ACTIVE");
        group.setCreatedBy(currentUser());
        group.setCreatedAt(OffsetDateTime.now());
        group.setUpdatedBy(currentUser());
        group.setUpdatedAt(OffsetDateTime.now());
        try {
            mapper.insert(group);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.of(ResultCode.CONFLICT, "用户组 code 已存在: " + group.getCode());
        }
        return group.getId();
    }

    @Transactional
    public void update(long id, IamCommands.UpdateGroup cmd) {
        require(id);
        mapper.updateGroup(TenantContext.get(), id, cmd.name().trim(), blankToNull(cmd.description()), currentUser());
    }

    @Transactional
    public void setEnabled(long id, boolean enabled) {
        require(id);
        if (mapper.updateStatus(TenantContext.get(), id, enabled ? "ACTIVE" : "DISABLED", currentUser()) > 0) {
            invalidation.all("user-group-status#" + id + "=" + enabled);
        }
    }

    public List<UserGroupMember> members(long groupId) {
        require(groupId);
        return mapper.selectMembers(TenantContext.get(), groupId);
    }

    @Transactional
    public int addMembers(long groupId, IamCommands.AddGroupMembers cmd) {
        UserGroup group = require(groupId);
        if (!"ACTIVE".equals(group.getStatus())) {
            throw BusinessException.of(ResultCode.CONFLICT, "禁用用户组不能新增成员");
        }
        OffsetDateTime from = cmd.validFrom() == null ? OffsetDateTime.now() : cmd.validFrom();
        if (cmd.validTo() != null && !cmd.validTo().isAfter(from)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "成员 validTo 必须晚于 validFrom");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String userId : cmd.userIds()) unique.add(userId.trim());
        if (unique.size() > 500) throw BusinessException.of(ResultCode.BAD_REQUEST, "单次最多加入 500 人");
        for (String id : unique) {
            try {
                org.getEmployeeByUserId(id);
            } catch (BusinessException e) {
                if (e.resultCode() != ResultCode.EMPLOYEE_NOT_FOUND) throw e;
                throw BusinessException.of(ResultCode.BAD_REQUEST, "员工不存在: " + id);
            }
            mapper.upsertMember(TenantContext.get(), groupId, id, from, cmd.validTo(), currentUser());
        }
        if (!unique.isEmpty()) invalidation.all("user-group-members-add#" + groupId);
        return unique.size();
    }

    @Transactional
    public void removeMember(long groupId, String userId) {
        require(groupId);
        if (mapper.revokeMember(TenantContext.get(), groupId, userId, currentUser()) == 0) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "用户组成员不存在: " + userId);
        }
        invalidation.all("user-group-member-remove#" + groupId);
    }

    private static String normalizeCode(String code) {
        String value = code.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_-]{1,63}")) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "用户组 code 需为 2~64 位大写字母/数字/_/-");
        }
        return value;
    }

    private static String normalizeStatus(String status) {
        String value = status.toUpperCase(Locale.ROOT);
        if (!value.equals("ACTIVE") && !value.equals("DISABLED")) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "非法用户组状态: " + status);
        }
        return value;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String currentUser() {
        var ctx = UserContextHolder.peek();
        return ctx == null ? "system" : ctx.userId();
    }
}
