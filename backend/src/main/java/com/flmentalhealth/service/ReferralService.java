package com.flmentalhealth.service;

import com.flmentalhealth.dto.Dtos.ReferralDtos;
import com.flmentalhealth.entity.*;
import com.flmentalhealth.entity.ReferralRequest.Status;
import com.flmentalhealth.exception.ApiExceptions.DuplicateReferralException;
import com.flmentalhealth.exception.ApiExceptions.ForbiddenOperationException;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.repository.ProviderRepository;
import com.flmentalhealth.repository.ReferralRequestRepository;
import com.flmentalhealth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * The referral workflow: submit, track, withdraw, and resolve.
 *
 * This is the most interesting class in the project. Everything else is
 * reading and writing rows; this one has to keep two things true at
 * once - a provider's capacity must never be oversubscribed, and no
 * referral may change status without an audit row recording who moved
 * it and why.
 */
@Service
@Transactional(readOnly = true)
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    /** The only statuses a navigator may resolve a referral to. */
    private static final Set<Status> DECIDABLE =
            Set.of(Status.ACCEPTED, Status.WAITLISTED, Status.DECLINED);

    private static final String NOT_FOUND = " not found";

    private final ReferralRequestRepository referralRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;

    public ReferralService(ReferralRequestRepository referralRepository,
                           ProviderRepository providerRepository,
                           UserRepository userRepository) {
        this.referralRepository = referralRepository;
        this.providerRepository = providerRepository;
        this.userRepository = userRepository;
    }

    // =================================================================
    // Submitting
    // =================================================================

    /**
     * Creates a PENDING referral and its first history row.
     *
     * The duplicate check enforces a rule MySQL cannot express: there is
     * no partial unique index for "unique only when status = 'PENDING'",
     * so a user may have many closed referrals to one provider but only
     * ever one open one. Documented here rather than pretended at the
     * schema level.
     */
    @Transactional
    public ReferralDtos.Response submit(Long userId, ReferralDtos.Request request) {

        User user = findUser(userId);

        Provider provider = providerRepository.findById(request.providerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider " + request.providerId() + NOT_FOUND));

        if (!provider.isActive()) {
            throw new ResourceNotFoundException(
                    "Provider " + request.providerId() + NOT_FOUND);
        }

        List<ReferralRequest> open = referralRepository
                .findByUserIdAndProviderIdAndStatus(userId, provider.getId(), Status.PENDING);

        if (!open.isEmpty()) {
            throw new DuplicateReferralException(
                    "You already have a pending request with this provider");
        }

        ReferralRequest referral = new ReferralRequest(
                user,
                provider,
                request.message(),
                parseContact(request.preferredContact()));

        // The creation row. Cascade from ReferralRequest persists it
        // alongside the referral, in this same transaction.
        referral.addHistory(ReferralStatusHistory.created(user));

        ReferralRequest saved = referralRepository.save(referral);
        log.info("Referral {} submitted by user {} to provider {}",
                saved.getId(), userId, provider.getId());

        return toResponse(saved);
    }

    // =================================================================
    // Reading
    // =================================================================

    public Page<ReferralDtos.Response> listMine(Long userId, Pageable pageable) {
        return referralRepository
                .findByUserIdOrderBySubmittedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    /** The navigator queue - oldest first, so nothing ages out of sight. */
    public Page<ReferralDtos.Response> queue(Status status, Pageable pageable) {
        return referralRepository
                .findByStatusOrderBySubmittedAtAsc(status, pageable)
                .map(this::toResponse);
    }

    /**
     * A referral is readable by the person who submitted it or by staff.
     * The ownership check happens here rather than in the controller
     * because it is a rule about the domain, not about HTTP.
     */
    public ReferralDtos.Response getById(Long referralId, Long requesterId, boolean isAdmin) {
        ReferralRequest referral = findReferral(referralId);

        if (!isAdmin && !referral.getUser().getId().equals(requesterId)) {
            throw new ForbiddenOperationException(
                    "You do not have access to this referral");
        }

        return toResponse(referral);
    }

    // =================================================================
    // Withdrawing - the client's own action
    // =================================================================

    @Transactional
    public ReferralDtos.Response withdraw(Long referralId, Long userId) {
        ReferralRequest referral = findReferral(referralId);

        if (!referral.getUser().getId().equals(userId)) {
            throw new ForbiddenOperationException(
                    "You can only withdraw your own referrals");
        }

        if (!referral.isOpen()) {
            throw new IllegalStateException(
                    "This referral is already " + referral.getStatus()
                            + " and cannot be withdrawn");
        }

        // The actor is the client, so transitionTo leaves resolvedBy
        // null - a withdrawal is not a staff resolution.
        referral.transitionTo(Status.WITHDRAWN, "Withdrawn by client", referral.getUser());

        log.info("Referral {} withdrawn by user {}", referralId, userId);
        return toResponse(referral);
    }

    // =================================================================
    // Deciding - the transactional core
    // =================================================================

    /**
     * Resolves a pending referral.
     *
     * The whole method is one transaction, and that is the point. Four
     * things have to happen together or not at all:
     *
     *   1. the provider's capacity is re-read UNDER A LOCK
     *   2. capacity is adjusted - a slot consumed, or the waitlist grown
     *   3. the referral's status, resolvedAt and resolvedBy are set
     *   4. a history row is written recording the transition
     *
     * If any step fails, all of it rolls back: no half-decremented
     * capacity, and no status change without its audit row.
     *
     * THE INTERESTING CASE: a navigator clicks Accept, but the
     * provider's last slot was taken between the client submitting and
     * the navigator reviewing. Overbooking would be wrong, and failing
     * outright would lose the request. So the referral resolves to
     * WAITLISTED instead, and the response reports the status that was
     * actually applied rather than the one that was asked for - the
     * caller is never told something different from what happened.
     */
    @Transactional
    public ReferralDtos.Response decide(Long referralId,
                                        ReferralDtos.DecisionRequest decision,
                                        Long staffId) {

        ReferralRequest referral = findReferral(referralId);

        if (!referral.isOpen()) {
            throw new IllegalStateException(
                    "Referral " + referralId + " is already " + referral.getStatus());
        }

        Status requested = parseStatus(decision.status());
        if (!DECIDABLE.contains(requested)) {
            throw new IllegalArgumentException(
                    "A referral can only be resolved to ACCEPTED, WAITLISTED or DECLINED");
        }

        User staff = findUser(staffId);

        Status applied = requested;
        String note = decision.note();

        if (requested == Status.ACCEPTED) {
            /*
             * Re-read the provider under a pessimistic write lock. The
             * copy hanging off the referral may be stale, and without
             * the lock two concurrent accepts could both see the same
             * last open slot.
             */
            Provider provider = providerRepository
                    .findByIdForUpdate(referral.getProvider().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Provider no longer exists"));

            if (provider.canAcceptReferral()) {
                provider.consumeSlot();
            } else {
                // No capacity left. Waitlist rather than overbook.
                applied = Status.WAITLISTED;
                provider.addToWaitlist();
                note = "No open slots at review time - added to waitlist"
                        + (note == null || note.isBlank() ? "" : ". " + note);

                log.info("Referral {} requested ACCEPTED but provider {} is at "
                                + "capacity - resolving to WAITLISTED",
                        referralId, provider.getId());
            }

        } else if (requested == Status.WAITLISTED) {
            Provider provider = providerRepository
                    .findByIdForUpdate(referral.getProvider().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Provider no longer exists"));
            provider.addToWaitlist();
        }
        // DECLINED touches no capacity.

        referral.transitionTo(applied, note, staff);

        log.info("Referral {} resolved to {} by staff {}", referralId, applied, staffId);
        return toResponse(referral);
    }

    // =================================================================
    // Helpers
    // =================================================================

    private ReferralRequest findReferral(Long id) {
        return referralRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Referral " + id + NOT_FOUND));
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User " + id + NOT_FOUND));
    }

    private Status parseStatus(String value) {
        try {
            return Status.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException _) {
            throw new IllegalArgumentException(
                    "'" + value + "' is not a valid status. Valid values: "
                            + java.util.Arrays.toString(Status.values()));
        }
    }

    private ReferralRequest.ContactPreference parseContact(String value) {
        try {
            return ReferralRequest.ContactPreference.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException _) {
            throw new IllegalArgumentException(
                    "'" + value + "' is not a valid contact preference. Valid values: "
                            + java.util.Arrays.toString(
                                    ReferralRequest.ContactPreference.values()));
        }
    }

    // ---------- mapping ----------

    private ReferralDtos.Response toResponse(ReferralRequest r) {
        Provider p = r.getProvider();

        return new ReferralDtos.Response(
                r.getId(),
                r.getStatus().name(),
                p.getId(),
                p.getFullName(),
                p.getOrganization().getName(),
                r.getUser().getFullName(),
                r.getMessage(),
                r.getPreferredContact().name(),
                r.getSubmittedAt().toString(),
                r.getResolvedAt() == null ? null : r.getResolvedAt().toString(),
                r.getResolvedBy() == null ? null : r.getResolvedBy().getFullName(),
                r.getHistory().stream().map(this::toHistoryEntry).toList());
    }

    private ReferralDtos.HistoryEntry toHistoryEntry(ReferralStatusHistory h) {
        return new ReferralDtos.HistoryEntry(
                h.getFromStatus() == null ? null : h.getFromStatus().name(),
                h.getToStatus().name(),
                h.getNote(),
                h.getChangedBy() == null ? null : h.getChangedBy().getFullName(),
                h.getCreatedAt() == null ? null : h.getCreatedAt().toString());
    }
}
