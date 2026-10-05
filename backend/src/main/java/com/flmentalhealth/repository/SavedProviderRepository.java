package com.flmentalhealth.repository;

import com.flmentalhealth.entity.SavedProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedProviderRepository extends JpaRepository<SavedProvider, Long> {

    /**
     * A user's shortlist, newest first.
     *
     * @EntityGraph fetches the provider and its organization in the same
     * query. Without it, mapping a 10-item shortlist would fire 20 extra
     * SELECTs - the N+1 problem in miniature.
     */
    @EntityGraph(attributePaths = {"provider", "provider.organization"})
    List<SavedProvider> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndProviderId(Long userId, Long providerId);

    Optional<SavedProvider> findByUserIdAndProviderId(Long userId, Long providerId);
}
