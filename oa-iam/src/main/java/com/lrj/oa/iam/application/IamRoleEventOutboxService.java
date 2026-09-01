package com.lrj.oa.iam.application;

import com.lrj.oa.iam.api.IamRoleEventOutboxApi;
import com.lrj.oa.iam.infrastructure.mapper.IamOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class IamRoleEventOutboxService implements IamRoleEventOutboxApi {
    private static final Set<String> STATUSES = Set.of("PENDING", "PROCESSING", "SENT", "FAILED");
    private final IamOutboxMapper mapper;

    public IamRoleEventOutboxService(IamOutboxMapper mapper) { this.mapper = mapper; }

    @Override
    @Transactional
    public List<PendingEvent> claimPending(int limit, String claimant) {
        int bounded = Math.min(Math.max(limit, 1), 500);
        return mapper.claimPending(bounded, claimant).stream()
                .map(row -> new PendingEvent(row.getId(), row.getEventId(), row.getTenantId(),
                        row.getTopic(), row.getMessageKey(), row.getPayload(), row.getAttempts()))
                .toList();
    }

    @Override
    @Transactional
    public void markSent(long id, String claimant) { mapper.markSent(id, claimant); }

    @Override
    @Transactional
    public void markFailed(long id, String claimant, String error) {
        String bounded = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 4000));
        mapper.markFailed(id, claimant, bounded);
    }

    @Override
    public long count(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("Unsupported outbox status: " + status);
        return mapper.countByStatus(normalized);
    }
}
