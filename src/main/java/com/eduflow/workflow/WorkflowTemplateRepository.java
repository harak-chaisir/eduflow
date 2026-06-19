package com.eduflow.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link WorkflowTemplate}.
 *
 * <p>All finder methods are tenant-scoped. Use {@link JpaSpecificationExecutor} with
 * {@link WorkflowSpecification} for dynamic list/search queries.</p>
 */
public interface WorkflowTemplateRepository
        extends JpaRepository<WorkflowTemplate, UUID>, JpaSpecificationExecutor<WorkflowTemplate> {

    Optional<WorkflowTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    List<WorkflowTemplate> findByTenantId(UUID tenantId);

    /** Highest existing version for a template name in a tenant; drives clone versioning. */
    Optional<WorkflowTemplate> findFirstByTenantIdAndNameOrderByVersionDesc(UUID tenantId, String name);

    boolean existsByTenantIdAndName(UUID tenantId, String name);

    long countByTenantIdAndActiveTrueAndArchivedFalse(UUID tenantId);
}
