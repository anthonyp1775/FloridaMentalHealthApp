package com.flmentalhealth.repository;

import com.flmentalhealth.entity.ReferralRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReferralRequestRepository
        extends JpaRepository<ReferralRequest, Long> {

    /** A person's own referrals, newest first. */
    Page<ReferralRequest> findByUserIdOrderBySubmittedAtDesc(
            Long userId, Pageable pageable);

    /** The admin queue: oldest pending first. */
    Page<ReferralRequest> findByStatusOrderBySubmittedAtAsc(
            ReferralRequest.Status status, Pageable pageable);

    /**
     * Backs the service-layer rule that a person cannot have two open
     * requests to the same provider - the constraint MySQL cannot
     * express as a partial unique index.
     */
    List<ReferralRequest> findByUserIdAndProviderIdAndStatus(
            Long userId, Long providerId, ReferralRequest.Status status);

    long countByStatus(ReferralRequest.Status status);

    // ---------- reporting ----------

    long countBySubmittedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusAndSubmittedAtBetween(ReferralRequest.Status status,
                                            LocalDateTime from,
                                            LocalDateTime to);
}
