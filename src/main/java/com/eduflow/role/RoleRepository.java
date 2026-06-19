package com.eduflow.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Role} entities.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    /** Lists the platform-wide system roles (tenant is null), ordered by name. */
    List<Role> findByTenantIsNullOrderByNameAsc();
}

