package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.IdentitySyncDtos.CreateSyncJob;
import com.lrj.oa.iam.api.dto.IdentitySyncDtos.SyncJobView;
import com.lrj.oa.iam.application.IdentityService.SyncOutcome;
import com.lrj.oa.iam.application.IdentityService.SyncWriteCounts;
import com.lrj.oa.iam.domain.IdentitySyncJob;
import com.lrj.oa.iam.infrastructure.mapper.IdentitySyncJobMapper;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 身份同步。请求内跑完，不引入独立 job-service。
 * INTERNAL=员工投影；MOCK=内置目录。无 LDAP。
 */
@Service
public class IdentitySyncService {

    private static final Logger log = LoggerFactory.getLogger(IdentitySyncService.class);
    private static final Set<String> CONNECTORS = Set.of("INTERNAL", "MOCK");
    private static final Set<String> MODES = Set.of("FULL", "INCREMENTAL");

    /** MOCK 连接器的远程目录。重复拉取同一批键，靠 identity 唯一约束去重。 */
    static final List<MockDirectoryEntry> MOCK_DIRECTORY = List.of(
            new MockDirectoryEntry("SERVICE_ACCOUNT", "mock:sa-directory", "MOCK 目录同步账号"),
            new MockDirectoryEntry("SERVICE_ACCOUNT", "mock:sa-payroll", "MOCK 薪酬同步账号"),
            new MockDirectoryEntry("API_CLIENT", "mock:api-webhook", "MOCK Webhook 客户端")
    );

    private final IdentitySyncJobMapper jobs;
    private final IdentityService identities;

    public IdentitySyncService(IdentitySyncJobMapper jobs, IdentityService identities) {
        this.jobs = jobs;
        this.identities = identities;
    }

    @Transactional
    public SyncJobView start(CreateSyncJob cmd) {
        String connector = cmd == null || !StringUtils.hasText(cmd.connector())
                ? "INTERNAL" : cmd.connector().trim().toUpperCase();
        if ("LDAP".equals(connector) || "HR".equals(connector) || "CASDOOR".equals(connector)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "本期无真实外部目录，仅支持 INTERNAL / MOCK");
        }
        if (!CONNECTORS.contains(connector)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "未知连接器: " + connector);
        }
        String mode = cmd == null || !StringUtils.hasText(cmd.mode())
                ? "FULL" : cmd.mode().trim().toUpperCase();
        if (!MODES.contains(mode)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "未知同步模式: " + mode);
        }

        long tenantId = TenantContext.get();
        String commandId = StringUtils.hasText(cmd == null ? null : cmd.commandId())
                ? cmd.commandId().trim() : UUID.randomUUID().toString();
        IdentitySyncJob existing = jobs.selectByCommandId(tenantId, commandId);
        if (existing != null) {
            return toView(existing);
        }

        String actor = UserContextHolder.require().userId();
        OffsetDateTime now = OffsetDateTime.now();
        IdentitySyncJob row = new IdentitySyncJob();
        row.setTenantId(tenantId);
        row.setCommandId(commandId);
        row.setConnector(connector);
        row.setMode(mode);
        row.setStatus("RUNNING");
        row.setCreatedCount(0);
        row.setUpdatedCount(0);
        row.setSkippedCount(0);
        row.setStartedAt(now);
        row.setCreatedBy(actor);
        row.setCreatedAt(now);
        try {
            jobs.insert(row);
        } catch (DuplicateKeyException ex) {
            IdentitySyncJob raced = jobs.selectByCommandId(tenantId, commandId);
            if (raced != null) {
                return toView(raced);
            }
            throw BusinessException.of(ResultCode.CONFLICT, "同步任务幂等键冲突");
        }

        try {
            SyncWriteCounts counts = run(connector, "FULL".equals(mode));
            row.setStatus("SUCCEEDED");
            row.setCreatedCount(counts.created());
            row.setUpdatedCount(counts.updated());
            row.setSkippedCount(counts.skipped());
            row.setFinishedAt(OffsetDateTime.now());
            jobs.finish(row);
            log.info("身份同步完成 job={} connector={} created={} updated={} skipped={}",
                    row.getId(), connector, counts.created(), counts.updated(), counts.skipped());
        } catch (RuntimeException ex) {
            row.setStatus("FAILED");
            row.setErrorMessage(ex.getMessage());
            row.setFinishedAt(OffsetDateTime.now());
            jobs.finish(row);
            throw ex;
        }
        return toView(jobs.selectById(tenantId, row.getId()));
    }

    public SyncJobView get(long id) {
        IdentitySyncJob row = jobs.selectById(TenantContext.get(), id);
        if (row == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "同步任务不存在: " + id);
        }
        return toView(row);
    }

    private SyncWriteCounts run(String connector, boolean full) {
        if ("INTERNAL".equals(connector)) {
            return identities.syncFromEmployees(full);
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (MockDirectoryEntry entry : MOCK_DIRECTORY) {
            SyncOutcome outcome = identities.upsertSyncedNhi(
                    entry.identityType(), entry.externalKey(), entry.displayName(), "MOCK", full);
            switch (outcome) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case SKIPPED -> skipped++;
            }
        }
        return new SyncWriteCounts(created, updated, skipped);
    }

    private static SyncJobView toView(IdentitySyncJob row) {
        return new SyncJobView(
                row.getId(), row.getCommandId(), row.getConnector(), row.getMode(), row.getStatus(),
                n(row.getCreatedCount()), n(row.getUpdatedCount()), n(row.getSkippedCount()),
                row.getErrorMessage(), row.getStartedAt(), row.getFinishedAt(),
                row.getCreatedBy(), row.getCreatedAt());
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    record MockDirectoryEntry(String identityType, String externalKey, String displayName) {}
}
