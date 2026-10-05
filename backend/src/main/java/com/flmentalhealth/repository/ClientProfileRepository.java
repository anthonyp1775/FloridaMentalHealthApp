package com.flmentalhealth.repository;

import com.flmentalhealth.entity.ClientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The primary key IS the user id - ClientProfile uses a shared primary
 * key via @MapsId - so findById(userId) is the natural lookup and no
 * findByUserId method is needed.
 */
@Repository
public interface ClientProfileRepository extends JpaRepository<ClientProfile, Long> {
}
