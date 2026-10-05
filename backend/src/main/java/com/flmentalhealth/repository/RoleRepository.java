package com.flmentalhealth.repository;

import com.flmentalhealth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /** Role names include the ROLE_ prefix: "ROLE_USER", "ROLE_ADMIN". */
    Optional<Role> findByName(String name);
}
