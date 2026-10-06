package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * One recorded change to a referral's status.
 *
 * Append-only by convention: rows are written, never updated or
 * deleted, so the sequence for a referral is the complete story of what
 * happened to it and who did it. Summing is not the point here - the
 * check that matters is that the LATEST row's toStatus always equals
 * the referral's current status, which is query 1 in seed.sql.
 *
 * Writing the row and changing the status happen in the same
 * transaction, via ReferralRequest.transitionTo(), so one cannot occur
 * without the other.
 */
@Entity
@Table(name = "referral_status_history")
@Getter
@Setter
@NoArgsConstructor
public class ReferralStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referral_request_id", nullable = false)
    private ReferralRequest referralRequest;

    /**
     * NULL only on the creation row - there was no prior status.
     * The database additionally enforces
     * CHECK (from_status IS NULL OR from_status <> to_status),
     * because a "change" that changes nothing is a bug, not an event.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ReferralRequest.Status fromStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ReferralRequest.Status toStatus;

    @Size(max = 255)
    @Column(name = "note", length = 255)
    private String note;

    /**
     * Who made the change. Nullable only because ON DELETE SET NULL
     * means removing a user account does not erase the history of what
     * they did - the record survives, the attribution does not.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    /**
     * Set in the constructor, NOT by @CreationTimestamp.
     *
     * @CreationTimestamp is populated by Hibernate at INSERT time,
     * which happens on flush - at commit. ReferralService creates a
     * history row and maps it to a response while the transaction is
     * still open, so with @CreationTimestamp this field would still be
     * null at that moment and the mapping would NPE.
     *
     * Assigning it here means the value exists from the instant the
     * object does, which is what any caller reasonably expects.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.systemDefault());

    public ReferralStatusHistory(ReferralRequest.Status fromStatus,
                                 ReferralRequest.Status toStatus,
                                 String note,
                                 User changedBy) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.changedBy = changedBy;
        this.createdAt = LocalDateTime.now(ZoneId.systemDefault());
    }

    /** The creation row for a newly submitted request. */
    public static ReferralStatusHistory created(User client) {
        return new ReferralStatusHistory(
                null,
                ReferralRequest.Status.PENDING,
                "Request submitted by client",
                client);
    }

    /** True for the row that records the request being submitted. */
    public boolean isCreation() {
        return fromStatus == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReferralStatusHistory other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ReferralStatusHistory{id=" + id
                + ", " + fromStatus + " -> " + toStatus + "}";
    }
}
