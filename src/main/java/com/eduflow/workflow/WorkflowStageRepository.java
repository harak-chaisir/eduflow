package com.eduflow.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link WorkflowStage}. All finders are tenant-scoped.
 */
public interface WorkflowStageRepository extends JpaRepository<WorkflowStage, UUID> {

    Optional<WorkflowStage> findByIdAndTenantId(UUID id, UUID tenantId);

    List<WorkflowStage> findByTemplateIdOrderByDisplayOrderAsc(UUID templateId);

    boolean existsByTemplateIdAndCode(UUID templateId, String code);
}
