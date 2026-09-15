package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.RiskFindingDtos.ChangeStatusRequest;
import com.lrj.oa.iam.api.dto.RiskFindingDtos.RiskFindingPage;
import com.lrj.oa.iam.api.dto.RiskFindingDtos.RiskFindingView;
import com.lrj.oa.iam.api.dto.RiskFindingDtos.ScanResult;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.RiskFinding;
import com.lrj.oa.iam.infrastructure.mapper.RiskFindingMapper;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 风险发现。规则内置，扫描在请求内跑完，不引入 oa-job。
 * 长期未用只扫 NHI：Human 热路径不写 decision_log。
 */
@Service
public class RiskFindingService {

    private static final Logger log = LoggerFactory.getLogger(RiskFindingService.class);
    static final int STALE_DAYS = 90;
    private static final int RULE_COUNT = 3;
    private static final Set<String> STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED", "IGNORED");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "OPEN", Set.of("ACKNOWLEDGED", "RESOLVED", "IGNORED"),
            "ACKNOWLEDGED", Set.of("RESOLVED", "IGNORED")
    );

    private final RiskFindingMapper findings;

    public RiskFindingService(RiskFindingMapper findings) {
        this.findings = findings;
    }

    @Transactional
    public ScanResult scan() {
        long tenantId = TenantContext.get();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime since = now.minusDays(STALE_DAYS);
        int opened = 0;
        opened += openAll(tenantId, now, "STALE_UNUSED", "MEDIUM",
                "超过 " + STALE_DAYS + " 天无 Check ALLOW",
                findings.selectStaleUnused(tenantId, since));
        opened += openAll(tenantId, now, "LEAVER_RESIDUAL", "HIGH",
                "停用后仍有有效 USER 授权或权限委托",
                findings.selectLeaverResidual(tenantId, now));
        opened += openAll(tenantId, now, "AGENT_ORPHAN", "HIGH",
                "Agent 无有效属主",
                findings.selectOrphanAgents(tenantId));
        log.info("风险扫描完成 tenant={} opened={}", tenantId, opened);
        return new ScanResult(opened, RULE_COUNT);
    }

    public RiskFindingPage list(String status, Long cursor, int size) {
        if (StringUtils.hasText(status) && !STATUSES.contains(status)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "未知状态: " + status);
        }
        int limit = Math.min(Math.max(size, 1), 200);
        List<RiskFinding> rows = findings.selectPage(TenantContext.get(),
                StringUtils.hasText(status) ? status : null, cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }
        String next = hasMore && !rows.isEmpty() ? String.valueOf(rows.getLast().getId()) : null;
        return new RiskFindingPage(rows.stream().map(RiskFindingService::toView).toList(), next, hasMore);
    }

    @Transactional
    public RiskFindingView changeStatus(long id, ChangeStatusRequest body) {
        if (body == null || !StringUtils.hasText(body.status())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "目标状态必填");
        }
        String to = body.status().trim();
        RiskFinding row = findings.selectById(TenantContext.get(), id);
        if (row == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "风险发现不存在");
        }
        Set<String> allowed = TRANSITIONS.getOrDefault(row.getStatus(), Set.of());
        if (!allowed.contains(to)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST,
                    "不能从 " + row.getStatus() + " 转到 " + to);
        }
        int n = findings.updateStatus(TenantContext.get(), id, row.getStatus(), to,
                body.note(), UserContextHolder.require().userId(), OffsetDateTime.now());
        if (n == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "状态已变更，请刷新后重试");
        }
        return toView(findings.selectById(TenantContext.get(), id));
    }

    private int openAll(long tenantId, OffsetDateTime now, String ruleCode, String severity,
                        String summary, List<Identity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int opened = 0;
        for (Identity identity : candidates) {
            RiskFinding row = new RiskFinding();
            row.setTenantId(tenantId);
            row.setRuleCode(ruleCode);
            row.setSeverity(severity);
            row.setIdentityId(identity.getId());
            row.setIdentityType(identity.getIdentityType());
            row.setDisplayName(identity.getDisplayName());
            row.setExternalKey(identity.getExternalKey());
            row.setSummary(summary);
            row.setDetectedAt(now);
            row.setUpdatedAt(now);
            try {
                if (findings.insertOpen(row) > 0) {
                    opened++;
                }
            } catch (DuplicateKeyException ex) {
                // 并发扫描同一身份，唯一索引兜底
            }
        }
        return opened;
    }

    private static RiskFindingView toView(RiskFinding row) {
        return new RiskFindingView(
                row.getId(),
                row.getRuleCode(),
                row.getSeverity(),
                row.getIdentityId(),
                row.getIdentityType(),
                row.getDisplayName(),
                row.getExternalKey(),
                row.getSummary(),
                row.getStatus(),
                row.getDetectedAt(),
                row.getUpdatedAt(),
                row.getUpdatedBy(),
                row.getNote());
    }
}
