package com.eduflow.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Active caseload for one counselor: students assigned to them in this tenant,
     * excluding {@code INACTIVE}, ordered by name. Used by the staff detail page.
     */
    List<Student> findByAssignedCounselorIdAndTenantIdAndStatusNotOrderByFirstNameAsc(
            UUID counselorId, UUID tenantId, StudentStatus status);

    /**
     * Active caseload counts grouped by counselor for a tenant (excludes {@code INACTIVE}
     * students and unassigned rows). Returns {@code [counselorId, count]} pairs so the staff
     * roster can show each counselor's caseload without an N+1 query.
     */
    @Query("""
            SELECT s.assignedCounselor.id, COUNT(s)
            FROM Student s
            WHERE s.tenant.id = :tenantId
              AND s.assignedCounselor IS NOT NULL
              AND s.status <> com.eduflow.student.StudentStatus.INACTIVE
            GROUP BY s.assignedCounselor.id
            """)
    List<Object[]> countActiveCaseloadByCounselor(@Param("tenantId") UUID tenantId);
}

