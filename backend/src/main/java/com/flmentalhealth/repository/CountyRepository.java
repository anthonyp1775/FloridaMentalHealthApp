package com.flmentalhealth.repository;

import com.flmentalhealth.entity.County;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CountyRepository extends JpaRepository<County, Long> {
    /*
     * Nothing beyond JpaRepository. Counties are read as a complete
     * sorted list to populate the search filter - findAll(Sort) covers
     * it, and all 67 fit in one response.
     */
}
