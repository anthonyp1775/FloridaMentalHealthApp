package com.flmentalhealth.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public class ReferralDtos {

    /** What a person submits. Free text only - no clinical questions. */
    public record Request(
            @NotNull Long providerId,
            @Size(max = 2000) String message,
            @NotBlank String preferredContact) {}

    /** What staff send back when resolving one. */
    public record DecisionRequest(
            @NotBlank String status,            // ACCEPTED | WAITLISTED | DECLINED
            @Size(max = 255) String note) {}

    public record Response(
            Long id, String status,
            Long providerId, String providerName, String organization,
            String clientName, String message, String preferredContact,
            String submittedAt, String resolvedAt, String resolvedBy,
            List<HistoryEntry> history) {}

    /** One row of the audit trail, for the status timeline component. */
    public record HistoryEntry(
            String fromStatus, String toStatus,
            String note, String changedBy, String createdAt) {}
}
