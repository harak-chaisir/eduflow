package com.eduflow.university;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link University}.
 *
 * <p>Every finder is tenant-scoped — a query without a {@code tenantId} filter is a
 * security bug. Dynamic search runs through {@link UniversitySpecification}.</p>
 */
public interface UniversityRepository
        extends JpaRepository<University, UUID>, JpaSpecificationExecutor<University> {

    Optional<University> findByIdAndTenantId(UUID id, UUID tenantId);

    List<University> findByTenantId(UUID tenantId);

    /** Active universities in a tenant, ordered by name — drives course-form pickers. */
    List<University> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

    boolean existsByNameAndCountryAndTenantId(String name, String country, UUID tenantId);
}
