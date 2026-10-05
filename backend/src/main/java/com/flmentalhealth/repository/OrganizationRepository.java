package com.flmentalhealth.repository;

import com.flmentalhealth.entity.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByName(String name);

    /** Soft-deleted organizations never appear in listings. */
    Page<Organization> findByActiveTrue(Pageable pageable);
}
