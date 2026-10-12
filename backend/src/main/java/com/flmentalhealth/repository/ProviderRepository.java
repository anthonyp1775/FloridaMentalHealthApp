package com.flmentalhealth.repository;

import com.flmentalhealth.entity.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumber(String licenseNumber);

    Page<Provider> findByLastNameContainingIgnoreCaseAndActiveTrue(
            String lastName, Pageable pageable);

    /**
     * The flagship search. Every filter is optional - a NULL parameter
     * disables that clause, which is why each condition is written as
     * ":param IS NULL OR ...". One query instead of a combinatorial
     * explosion of method names.
     *
     * DISTINCT matters: joining the insurance collection multiplies
     * rows, so without it a provider accepting two plans appears twice.
     */
    /*
     * Fetches organization and county in the SAME query. Without
     * this, mapping 20 results to Summary fires 40 extra SELECTs -
     * the N+1 problem. Only to-one associations are fetched here;
     * adding a collection would force Hibernate to paginate in
     * memory (HHH000104) instead of in the database.
     */
    @EntityGraph(attributePaths = {"organization", "organization.county"})
    @Query("""
           SELECT DISTINCT p FROM Provider p
           JOIN p.organization o
           LEFT JOIN p.insurancePlans ip
           WHERE p.active = true
             AND (:countyId    IS NULL OR o.county.id = :countyId)
             AND (:insuranceId IS NULL OR ip.id       = :insuranceId)
             AND (:telehealth  IS NULL OR p.offersTelehealth = :telehealth)
             AND (:acceptingOnly = false OR p.acceptingNewClients = true)
           """)
    Page<Provider> search(@Param("countyId")     Long countyId,
                          @Param("insuranceId")  Long insuranceId,
                          @Param("telehealth")   Boolean telehealth,
                          @Param("acceptingOnly") boolean acceptingOnly,
                          Pageable pageable);

    /**
     * Reads a provider with a PESSIMISTIC_WRITE lock (SELECT ... FOR
     * UPDATE), used by ReferralService when resolving a referral.
     *
     * WHY: two navigators accepting referrals to the same provider at
     * the same moment could both read openSlots = 1, both decrement,
     * and leave the provider at 0 having promised two intakes. The lock
     * makes the second transaction wait until the first commits, so it
     * reads the already-decremented value and correctly waitlists.
     *
     * This is a short lock inside one transaction, on one row, and only
     * on the write path - it does not touch search.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Provider p WHERE p.id = :id")
    Optional<Provider> findByIdForUpdate(@Param("id") Long id);

    /**
     * ACCESS GAP: how many providers currently accepting new clients
     * take each plan type.
     *
     * COUNT(DISTINCT p.id) matters - a provider accepting three
     * Medicaid plans must count once for MEDICAID, not three times.
     *
     * Returns Object[] rows of {PlanType, Long} rather than a
     * projection interface, which keeps the query readable and avoids
     * tying the repository to a DTO shape.
     */
    @Query("""
           SELECT ip.planType, COUNT(DISTINCT p.id)
           FROM Provider p
           JOIN p.insurancePlans ip
           WHERE p.active = true AND p.acceptingNewClients = true
           GROUP BY ip.planType
           ORDER BY COUNT(DISTINCT p.id) DESC
           """)
    List<Object[]> accessGapByPlanType();

    /**
     * CAPACITY BY COUNTY: where the open slots and the waits actually
     * are. Joins provider -> organization -> county, which is why
     * county lives on the organization rather than being duplicated
     * onto every provider row.
     */
    @Query("""
           SELECT c.name, c.region,
                  COUNT(DISTINCT p.id),
                  COALESCE(SUM(p.openSlots), 0),
                  COALESCE(SUM(p.waitlistCount), 0)
           FROM Provider p
           JOIN p.organization o
           JOIN o.county c
           WHERE p.active = true
           GROUP BY c.id, c.name, c.region
           ORDER BY SUM(p.openSlots) DESC
           """)
    List<Object[]> capacityByCounty();

    /** Providers currently open for intake. */
    @Query("SELECT p FROM Provider p WHERE p.active = true "
         + "AND p.acceptingNewClients = true AND p.openSlots > 0")
    List<Provider> findWithOpenCapacity();
}
