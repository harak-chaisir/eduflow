package com.eduflow.university;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Course}.
 *
 * <p>Every finder is tenant-scoped — a query without a {@code tenantId} filter is a
 * security bug.</p>
 */
public interface CourseRepository
        extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {

    Optional<Course> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Course> findByUniversityIdAndTenantIdOrderByNameAsc(UUID universityId, UUID tenantId);

    /** Active courses in a tenant, ordered by name — drives the student "apply to course" picker. */
    List<Course> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

    boolean existsByNameAndUniversityIdAndTenantId(String name, UUID universityId, UUID tenantId);

    long countByUniversityIdAndTenantId(UUID universityId, UUID tenantId);
}
