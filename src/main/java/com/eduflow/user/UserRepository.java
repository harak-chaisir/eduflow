package com.eduflow.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link User} entities.
 *
 * <p>All queries that return tenant-scoped data filter by {@code tenantId}.
 * The only exception is {@link #findAllByEmailWithRoles(String)}, used by the
 * security layer to locate a user across tenants at login time.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds all users matching the given email (case-insensitive) and eagerly loads
     * their roles. Used by {@code EduFlowUserDetailsService} at authentication time.
     * A user's email may exist in multiple tenants, so a {@link List} is returned.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.tenant WHERE LOWER(u.email) = LOWER(:email)")
    List<User> findAllByEmailWithRoles(@Param("email") String email);

    /** Finds a single user by id scoped to the given tenant. */
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Lists all users belonging to a tenant. */
    List<User> findByTenantId(UUID tenantId);

    /** Counts all users belonging to a tenant. Used for plan staff-limit enforcement. */
    long countByTenantId(UUID tenantId);

    /**
     * Lists active users in a tenant who hold the given role, ordered by name.
     * Used to populate the counselor picker on the student form.
     */
    @Query("""
            SELECT DISTINCT u FROM User u JOIN u.roles r
            WHERE u.tenant.id = :tenantId
              AND r.name = :roleName
              AND u.status = com.eduflow.user.UserStatus.ACTIVE
            ORDER BY u.firstName, u.lastName
            """)
    List<User> findActiveByTenantIdAndRoleName(@Param("tenantId") UUID tenantId,
                                               @Param("roleName") String roleName);

    /** Checks whether an email is already registered within a tenant. */
    boolean existsByEmailIgnoreCaseAndTenantId(String email, UUID tenantId);
}

