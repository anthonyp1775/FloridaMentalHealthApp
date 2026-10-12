package com.flmentalhealth.repository;

import com.flmentalhealth.entity.InsurancePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InsurancePlanRepository extends JpaRepository<InsurancePlan, Long> {
    /*
     * Nothing beyond JpaRepository. Plans are read as a complete sorted
     * list for the filter; the access-gap report groups on plan type in
     * ProviderRepository, where the join to providers lives.
     */
}
