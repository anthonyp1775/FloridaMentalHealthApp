package com.flmentalhealth.repository;

import com.flmentalhealth.entity.Population;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PopulationRepository extends JpaRepository<Population, Long> {
    /*
     * Nothing beyond JpaRepository. Populations are read unsorted - the
     * seeded order runs youngest to oldest, which reads better in a
     * dropdown than alphabetical would.
     */
}
