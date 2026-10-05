package com.flmentalhealth.repository;

import com.flmentalhealth.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LanguageRepository extends JpaRepository<Language, Long> {
    /*
     * Nothing beyond JpaRepository. Languages are read as a complete
     * sorted list for the filter and resolved by id when a provider or
     * profile is saved.
     */
}
