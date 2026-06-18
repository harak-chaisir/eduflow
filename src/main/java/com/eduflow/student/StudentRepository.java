package com.eduflow.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Student}.
 *
 * <p>All finder methods that return tenant-scoped data require a {@code tenantId} argument.
 * Use {@link JpaSpecificationExecutor} for dynamic search queries via {@link StudentSpecification}.</p>
 */
public interface StudentRepository
        extends JpaRepository<Student, UUID>, JpaSpecificationExecutor<Student> {

    /**
     * Returns all students belonging to the given tenant.
     *
     * @param tenantId the tenant UUID
     * @return list of students (may be empty, never null)
     */
    List<Student> findByTenantId(UUID tenantId);

    /**
     * Finds a single student by primary key, scoped to the tenant.
     *
     * @param id       the student UUID
     * @param tenantId the tenant UUID
     * @return an Optional containing the student, or empty if not found / wrong tenant
     */
    Optional<Student> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Checks whether a student with the given email already exists in the tenant.
     * Used to prevent duplicate registrations.
     *
     * @param email    the email address to check
     * @param tenantId the tenant UUID
     * @return {@code true} if a student with that email exists in this tenant
     */
    boolean existsByEmailAndTenantId(String email, UUID tenantId);

    /** Total number of students in a tenant. */
    long countByTenantId(UUID tenantId);

    /** Number of students in a tenant with the given status. Drives the dashboard KPIs. */
    long countByTenantIdAndStatus(UUID tenantId, StudentStatus status);
}

