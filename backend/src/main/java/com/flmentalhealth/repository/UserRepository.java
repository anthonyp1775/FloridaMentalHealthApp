package com.flmentalhealth.repository;

import com.flmentalhealth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** The authentication lookup. Email is the login identifier. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
