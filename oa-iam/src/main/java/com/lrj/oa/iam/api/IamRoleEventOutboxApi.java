package com.lrj.oa.iam.api;

import java.util.List;

/** Typed delivery port for durable IAM role events. Infrastructure publishers live outside the IAM module. */
public interface IamRoleEventOutboxApi {

    record PendingEvent(long id, String eventId, long tenantId, String topic,
                        String messageKey, String payload, int attempts) { }

    List<PendingEvent> claimPending(int limit, String claimant);

    void markSent(long id, String claimant);

    void markFailed(long id, String claimant, String error);

    long count(String status);
}
