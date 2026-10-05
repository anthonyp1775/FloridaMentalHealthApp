package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A person asking to be connected to a provider. The core workflow.
 *
 * Owns its status history: saving a request cascades to its history
 * rows, which is what lets ReferralService write a status change and
 * its audit row in a single transaction.
 */
@Entity
@Table(name = "referral_requests")
@Getter
@Setter
@NoArgsConstructor
public class ReferralRequest {

    /** Referenced as ReferralRequest.Status. */
    public enum Status {
        /** Submitted, awaiting review. */
        PENDING,
        /** A slot was confirmed and intake is being scheduled. */
        ACCEPTED,
        /** No capacity at review time; added to the provider's waitlist. */
        WAITLISTED,
        /** The provider is not taking this referral. */
        DECLINED,
        /** The client withdrew it. */
        WITHDRAWN,
        /** Resolved and closed out administratively. */
        CLOSED;

        /** Whether a referral in this status can still be acted on. */
        public boolean isOpen() {
            return this == PENDING;
        }
    }

    /** Referenced as ReferralRequest.ContactPreference. */
    public enum ContactPreference { EMAIL, PHONE, TEXT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The person seeking care. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    /**
     * Free text the person writes themselves.
     *
     * The application never asks clinical screening questions and
     * stores no diagnosis. Whatever a client chooses to share here is
     * their own words, not a structured assessment.
     */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact", nullable = false, length = 20)
    private ContactPreference preferredContact = ContactPreference.EMAIL;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    /** Set when the request leaves PENDING. NULL while still open. */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** The staff member who resolved it. NULL while PENDING. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    /**
     * The audit trail. CascadeType.ALL + orphanRemoval means history
     * rows are saved with the request and deleted with it - they have
     * no meaning apart from it.
     */
    @OneToMany(
            mappedBy = "referralRequest",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("createdAt ASC, id ASC")
    private List<ReferralStatusHistory> history = new ArrayList<>();

    public ReferralRequest(User user, Provider provider,
                           String message, ContactPreference preferredContact) {
        this.user = user;
        this.provider = provider;
        this.message = message;
        this.preferredContact = preferredContact;
        this.status = Status.PENDING;
    }

    /**
     * Adds a history row and keeps BOTH sides of the relationship in
     * sync. Setting only one side is the most common JPA bug there is:
     * the object graph looks correct in memory but the foreign key is
     * never written.
     */
    public void addHistory(ReferralStatusHistory entry) {
        history.add(entry);
        entry.setReferralRequest(this);
    }

    /**
     * Moves to a new status and records it in one step, so a status
     * change without its audit row is not something a caller can
     * accidentally produce.
     *
     * @param newStatus where it is going
     * @param note      why, in a line
     * @param actor     who did it - the client for a withdrawal,
     *                  a staff member otherwise
     */
    public void transitionTo(Status newStatus, String note, User actor) {
        if (newStatus == this.status) {
            throw new IllegalArgumentException(
                    "Referral " + id + " is already " + newStatus);
        }

        ReferralStatusHistory entry =
                new ReferralStatusHistory(this.status, newStatus, note, actor);
        addHistory(entry);

        this.status = newStatus;

        if (!newStatus.isOpen()) {
            this.resolvedAt = LocalDateTime.now();
            // A withdrawal is the client's own action, not a staff
            // resolution, so resolvedBy stays null in that case.
            if (newStatus != Status.WITHDRAWN) {
                this.resolvedBy = actor;
            }
        }
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReferralRequest other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ReferralRequest{id=" + id + ", status=" + status + "}";
    }
}
