package com.lrj.oa.iam.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/** Versioned payload encoded inside the backward-compatible CacheInvalidation.key field. */
public record PermissionEpochInvalidation(long tenantId, long epoch, String eventId, String reason) {
    private static final String VERSION = "v1";

    public String encode() {
        String encodedReason = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((reason == null ? "" : reason).getBytes(StandardCharsets.UTF_8));
        return String.join("|", VERSION, String.valueOf(tenantId), String.valueOf(epoch),
                eventId == null ? "" : eventId, encodedReason);
    }

    public static Optional<PermissionEpochInvalidation> decode(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String[] parts = value.split("\\|", -1);
        if (parts.length != 5 || !VERSION.equals(parts[0])) return Optional.empty();
        try {
            long tenantId = Long.parseLong(parts[1]);
            long epoch = Long.parseLong(parts[2]);
            if (tenantId <= 0 || epoch < 0) return Optional.empty();
            String reason = new String(Base64.getUrlDecoder().decode(parts[4]), StandardCharsets.UTF_8);
            return Optional.of(new PermissionEpochInvalidation(tenantId, epoch, parts[3], reason));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
