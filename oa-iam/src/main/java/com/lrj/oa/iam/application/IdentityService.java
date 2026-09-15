package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.IdentityDtos;
import com.lrj.oa.iam.api.dto.IdentityDtos.CreateIdentity;
import com.lrj.oa.iam.api.dto.IdentityDtos.IdentityPage;
import com.lrj.oa.iam.api.dto.IdentityDtos.IdentityView;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.IdentityLabel;
import com.lrj.oa.iam.domain.IdentityStatus;
import com.lrj.oa.iam.domain.IdentityType;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 身份目录写模型。Human 只能由员工事件投影；NHI 在此创建。
 * 不改 {@code grant_record} 与权限快照热路径。
 */
@Service
public class IdentityService {

    private static final int MAX_PAGE = 200;
    private static final String ATTR_EMPTY = "{}";

    private final IdentityMapper mapper;
    private final CredentialService credentials;

    public IdentityService(IdentityMapper mapper, CredentialService credentials) {
        this.mapper = mapper;
        this.credentials = credentials;
    }

    public IdentityPage list(String type, String status, String q, Long cursor, int size) {
        int limit = Math.min(Math.max(size, 1), MAX_PAGE);
        if (StringUtils.hasText(type) && !IdentityType.isKnown(type)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "未知身份类型: " + type);
        }
        if (StringUtils.hasText(status) && !IdentityStatus.visibleNames().contains(status)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "未知或不可查询的状态: " + status);
        }
        List<Identity> rows = mapper.selectPage(TenantContext.get(), emptyToNull(type), emptyToNull(status),
                emptyToNull(q), cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }
        attachLabels(rows);
        String next = rows.isEmpty() ? "" : String.valueOf(rows.get(rows.size() - 1).getSeq());
        return new IdentityPage(rows.stream().map(this::toView).toList(), next, hasMore);
    }

    public IdentityView get(String identityId) {
        Identity row = requireVisible(identityId);
        row.setLabels(mapper.selectLabels(row.getId()));
        return toView(row);
    }

    @Transactional
    public IdentityView create(CreateIdentity cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.identityType()) || !IdentityType.isKnown(cmd.identityType())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "身份类型不合法");
        }
        IdentityType type = IdentityType.valueOf(cmd.identityType());
        if (type.human()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "USER 身份只能由员工档案投影，禁止手工创建");
        }
        if (!StringUtils.hasText(cmd.displayName()) || !StringUtils.hasText(cmd.externalKey())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "displayName 与 externalKey 必填");
        }
        String ownerId = emptyToNull(cmd.ownerIdentityId());
        if (type == IdentityType.AGENT) {
            requireUserOwner(ownerId);
        } else if (ownerId != null) {
            requireVisible(ownerId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Identity row = new Identity();
        row.setId(UUID.randomUUID().toString());
        row.setTenantId(TenantContext.get());
        row.setIdentityType(type.name());
        row.setDisplayName(cmd.displayName().trim());
        row.setSource("INTERNAL");
        row.setStatus(IdentityStatus.ACTIVE.name());
        row.setExternalKey(cmd.externalKey().trim());
        row.setAttributes(ATTR_EMPTY);
        row.setRiskLevel("LOW");
        row.setOwnerIdentityId(ownerId);
        row.setExpiredAt(cmd.expiredAt());
        row.setVersion(0);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.of(ResultCode.CONFLICT,
                    "同一类型下 externalKey 已存在: " + cmd.externalKey());
        }
        replaceLabels(row.getId(), defaultNhiLabels(type, cmd.labels()));
        return get(row.getId());
    }

    @Transactional
    public IdentityView update(String identityId, IdentityDtos.UpdateIdentity cmd) {
        if (cmd == null) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "请求体为空");
        }
        Identity current = requireVisible(identityId);
        if (cmd.ownerIdentityId() != null) {
            String ownerId = emptyToNull(cmd.ownerIdentityId());
            if (ownerId != null) {
                if (IdentityType.USER.name().equals(current.getIdentityType())) {
                    throw BusinessException.of(ResultCode.BAD_REQUEST, "USER 不能设置属主");
                }
                if (identityId.equals(ownerId)) {
                    throw BusinessException.of(ResultCode.BAD_REQUEST, "属主不能是自己");
                }
                requireVisible(ownerId);
            }
            current.setOwnerIdentityId(ownerId);
        }
        current.setDisplayName(StringUtils.hasText(cmd.displayName()) ? cmd.displayName().trim() : current.getDisplayName());
        current.setExpiredAt(cmd.expiredAt());
        current.setVersion(cmd.version());
        current.setUpdatedAt(OffsetDateTime.now());
        if (mapper.updateMutable(current) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "身份已被他人修改，请刷新后重试");
        }
        return get(identityId);
    }

    @Transactional
    public IdentityView changeStatus(String identityId, IdentityDtos.ChangeIdentityStatus cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.status())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "目标状态必填");
        }
        Identity current = requireVisible(identityId);
        IdentityStatus from;
        IdentityStatus to;
        try {
            from = IdentityStatus.valueOf(current.getStatus());
            to = IdentityStatus.valueOf(cmd.status());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "未知状态: " + cmd.status());
        }
        if (!from.canTransitTo(to)) {
            throw BusinessException.of(ResultCode.IDENTITY_STATUS_CONFLICT,
                    current.getStatus() + " 不能迁移到 " + cmd.status());
        }
        if (mapper.updateStatus(TenantContext.get(), identityId, to.name(), cmd.version(), OffsetDateTime.now()) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "身份已被他人修改，请刷新后重试");
        }
        if (to == IdentityStatus.DISABLED || to == IdentityStatus.EXPIRED || to == IdentityStatus.DELETED) {
            String actor = UserContextHolder.peek() == null ? "system" : UserContextHolder.peek().userId();
            credentials.revokeAllOfIdentity(identityId, actor);
        }
        if (to == IdentityStatus.DELETED) {
            current.setStatus(to.name());
            current.setVersion(cmd.version() + 1);
            current.setLabels(mapper.selectLabels(identityId));
            return toView(current);
        }
        return get(identityId);
    }

    @Transactional
    public IdentityView replaceLabels(String identityId, IdentityDtos.ReplaceIdentityLabels cmd) {
        if (cmd == null) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "请求体为空");
        }
        Identity current = requireVisible(identityId);
        if (current.getVersion() == null || current.getVersion() != cmd.version()) {
            throw BusinessException.of(ResultCode.CONFLICT, "身份版本不匹配");
        }
        replaceLabels(identityId, cmd.labels());
        // 标签替换也推进 version，避免并发覆盖
        current.setDisplayName(current.getDisplayName());
        current.setExpiredAt(current.getExpiredAt());
        current.setOwnerIdentityId(current.getOwnerIdentityId());
        current.setUpdatedAt(OffsetDateTime.now());
        if (mapper.updateMutable(current) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "身份已被他人修改，请刷新后重试");
        }
        return get(identityId);
    }

    /**
     * 员工投影：按 Casdoor sub 幂等 upsert USER 身份。
     */
    @Transactional
    public void projectUser(String userId, Long employeeId, String name, String employmentType,
                            String employeeStatus, Long orgId, String orgPath) {
        if (!StringUtils.hasText(userId) || employeeId == null) {
            return;
        }
        long tenantId = TenantContext.get();
        String identityStatus = toIdentityStatus(employeeStatus);
        String label = contractor(employmentType) ? IdentityLabel.CONTRACTOR.name() : IdentityLabel.EMPLOYEE.name();
        Identity existing = mapper.selectByExternalKey(tenantId, IdentityType.USER.name(), userId);
        OffsetDateTime now = OffsetDateTime.now();
        if (existing == null) {
            Identity row = new Identity();
            row.setId(UUID.randomUUID().toString());
            row.setTenantId(tenantId);
            row.setIdentityType(IdentityType.USER.name());
            row.setDisplayName(StringUtils.hasText(name) ? name : userId);
            row.setSource("CASDOOR");
            row.setStatus(identityStatus);
            row.setExternalKey(userId);
            row.setEmployeeId(employeeId);
            row.setOrgId(orgId);
            row.setOrgPath(orgPath);
            row.setAttributes(ATTR_EMPTY);
            row.setRiskLevel("LOW");
            row.setVersion(0);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            try {
                mapper.insert(row);
            } catch (DuplicateKeyException ex) {
                existing = mapper.selectByExternalKey(tenantId, IdentityType.USER.name(), userId);
                if (existing == null) {
                    throw ex;
                }
            }
            if (existing == null) {
                replaceLabels(row.getId(), List.of(label));
                return;
            }
        }
        mapper.updateProjection(tenantId, existing.getId(),
                StringUtils.hasText(name) ? name : existing.getDisplayName(),
                identityStatus, orgId, orgPath, now);
        replaceLabels(existing.getId(), List.of(label));
    }

    @Transactional
    public int backfillFromEmployees() {
        return syncFromEmployees(false).created();
    }

    /**
     * INTERNAL 连接器：先刷新已有投影，再补齐缺失。增量模式跳过刷新。
     */
    @Transactional
    public SyncWriteCounts syncFromEmployees(boolean refreshExisting) {
        int updated = refreshExisting ? mapper.refreshUsersFromEmployees() : 0;
        int created = mapper.backfillUsersFromEmployees();
        mapper.backfillUserLabels();
        return new SyncWriteCounts(created, updated, 0);
    }

    /**
     * MOCK / 外部目录 upsert。同一 (type, externalKey) 不双份。
     */
    @Transactional
    public SyncOutcome upsertSyncedNhi(String identityType, String externalKey, String displayName,
                                       String source, boolean updateExisting) {
        if (!StringUtils.hasText(identityType) || !IdentityType.isKnown(identityType)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "身份类型不合法");
        }
        IdentityType type = IdentityType.valueOf(identityType);
        if (type.human()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "USER 只能由员工投影同步");
        }
        if (!StringUtils.hasText(externalKey) || !StringUtils.hasText(displayName)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "externalKey 与 displayName 必填");
        }
        long tenantId = TenantContext.get();
        Identity existing = mapper.selectByExternalKey(tenantId, type.name(), externalKey.trim());
        if (existing != null) {
            if (!updateExisting) {
                return SyncOutcome.SKIPPED;
            }
            mapper.updateProjection(tenantId, existing.getId(), displayName.trim(),
                    existing.getStatus(), existing.getOrgId(), existing.getOrgPath(), OffsetDateTime.now());
            return SyncOutcome.UPDATED;
        }
        OffsetDateTime now = OffsetDateTime.now();
        Identity row = new Identity();
        row.setId(UUID.randomUUID().toString());
        row.setTenantId(tenantId);
        row.setIdentityType(type.name());
        row.setDisplayName(displayName.trim());
        row.setSource(StringUtils.hasText(source) ? source.trim() : "MOCK");
        row.setStatus(IdentityStatus.ACTIVE.name());
        row.setExternalKey(externalKey.trim());
        row.setAttributes(ATTR_EMPTY);
        row.setRiskLevel("LOW");
        row.setVersion(0);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException ex) {
            Identity raced = mapper.selectByExternalKey(tenantId, type.name(), externalKey.trim());
            if (raced == null) {
                throw BusinessException.of(ResultCode.CONFLICT, "同步写入冲突");
            }
            if (!updateExisting) {
                return SyncOutcome.SKIPPED;
            }
            mapper.updateProjection(tenantId, raced.getId(), displayName.trim(),
                    raced.getStatus(), raced.getOrgId(), raced.getOrgPath(), OffsetDateTime.now());
            return SyncOutcome.UPDATED;
        }
        replaceLabels(row.getId(), defaultNhiLabels(type, null));
        return SyncOutcome.CREATED;
    }

    public enum SyncOutcome { CREATED, UPDATED, SKIPPED }

    public record SyncWriteCounts(int created, int updated, int skipped) {}

    @Transactional
    public void disableByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        Identity existing = mapper.selectByExternalKey(TenantContext.get(), IdentityType.USER.name(), userId);
        if (existing == null || IdentityStatus.DELETED.name().equals(existing.getStatus())) {
            return;
        }
        mapper.updateProjection(TenantContext.get(), existing.getId(),
                existing.getDisplayName(), IdentityStatus.DISABLED.name(),
                existing.getOrgId(), existing.getOrgPath(), OffsetDateTime.now());
        credentials.revokeAllOfIdentity(existing.getId(), userId);
    }

    private Identity requireVisible(String identityId) {
        if (!StringUtils.hasText(identityId)) {
            throw BusinessException.of(ResultCode.IDENTITY_NOT_FOUND);
        }
        Identity row = mapper.selectById(TenantContext.get(), identityId);
        if (row == null || IdentityStatus.DELETED.name().equals(row.getStatus())) {
            throw BusinessException.of(ResultCode.IDENTITY_NOT_FOUND);
        }
        return row;
    }

    private void requireUserOwner(String ownerId) {
        if (!StringUtils.hasText(ownerId)) {
            throw BusinessException.of(ResultCode.NHI_OWNER_REQUIRED);
        }
        Identity owner = requireVisible(ownerId);
        if (!IdentityType.USER.name().equals(owner.getIdentityType())) {
            throw BusinessException.of(ResultCode.NHI_OWNER_REQUIRED, "Agent 属主必须是 USER 身份");
        }
    }

    private void replaceLabels(String identityId, List<String> labels) {
        mapper.deleteLabels(identityId);
        if (labels == null) {
            return;
        }
        for (String label : labels) {
            if (!StringUtils.hasText(label)) {
                continue;
            }
            String normalized = label.trim();
            if (!IdentityLabel.isKnown(normalized)) {
                throw BusinessException.of(ResultCode.BAD_REQUEST, "未知标签: " + normalized);
            }
            mapper.insertLabel(identityId, normalized);
        }
    }

    private List<String> defaultNhiLabels(IdentityType type, List<String> requested) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        if (type == IdentityType.AGENT) {
            return List.of(IdentityLabel.AGENT.name());
        }
        if (type == IdentityType.SERVICE_ACCOUNT || type == IdentityType.API_CLIENT
                || type == IdentityType.APPLICATION || type == IdentityType.AUTOMATION_WORKER) {
            return List.of(IdentityLabel.SERVICE_ACCOUNT.name());
        }
        return List.of();
    }

    private void attachLabels(List<Identity> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<String> ids = rows.stream().map(Identity::getId).toList();
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (IdentityMapper.IdentityLabelRow rel : mapper.selectLabelsByIds(ids)) {
            grouped.computeIfAbsent(rel.identityId(), key -> new ArrayList<>()).add(rel.label());
        }
        for (Identity row : rows) {
            row.setLabels(grouped.getOrDefault(row.getId(), List.of()));
        }
    }

    private IdentityView toView(Identity row) {
        List<String> labels = row.getLabels() == null ? List.of() : List.copyOf(row.getLabels());
        return new IdentityView(
                row.getId(),
                row.getTenantId() == null ? TenantContext.get() : row.getTenantId(),
                row.getIdentityType(),
                row.getDisplayName(),
                row.getSource(),
                row.getStatus(),
                row.getExternalKey(),
                row.getEmployeeId(),
                row.getOrgId(),
                row.getOrgPath(),
                row.getRiskLevel(),
                row.getOwnerIdentityId(),
                row.getExpiredAt(),
                row.getVersion() == null ? 0 : row.getVersion(),
                labels,
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private static String toIdentityStatus(String employeeStatus) {
        if ("LEFT".equals(employeeStatus)) {
            return IdentityStatus.DISABLED.name();
        }
        if ("LEAVING".equals(employeeStatus)) {
            return IdentityStatus.SUSPENDED.name();
        }
        return IdentityStatus.ACTIVE.name();
    }

    private static boolean contractor(String employmentType) {
        return "OUTSOURCE".equals(employmentType) || "CONSULTANT".equals(employmentType);
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
