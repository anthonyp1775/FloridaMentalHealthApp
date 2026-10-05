package com.flmentalhealth.repository;

import com.flmentalhealth.entity.ReferralStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReferralStatusHistoryRepository extends JpaRepository<ReferralStatusHistory, Long> {
    /*
     * Nothing beyond JpaRepository, and that is deliberate. History rows
     * are never queried directly: they are written through
     * ReferralRequest.addHistory() and read as part of the referral that
     * owns them, ordered by @OrderBy on the association. Giving this
     * repository finder methods would invite reading the audit trail
     * away from the record it explains.
     */
}
