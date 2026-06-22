package com.eduflow.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Application}.
 *
 * <p>Every finder is tenant-scoped — a query without a {@code tenantId} filter is a
 * security bug. Dynamic search runs through {@link ApplicationSpecification}.</p>
 */
public interface ApplicationRepository
        extends JpaRepository<Application, UUID>, JpaSpecificationExecutor<Application> {

    Optional<Application> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Application> findByStudentIdAndTenantIdOrderByCreatedAtDesc(UUID studentId, UUID tenantId);

    boolean existsByStudentIdAndCourseIdAndTenantId(UUID studentId, UUID courseId, UUID tenantId);
}
