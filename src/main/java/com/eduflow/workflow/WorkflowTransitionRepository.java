package com.eduflow.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link WorkflowTransition}. All finders are tenant-scoped.
 */
public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, UUID> {

    Optional<WorkflowTransition> findByIdAndTenantId(UUID id, UUID tenantId);

    List<WorkflowTransition> findByTemplateId(UUID templateId);

    List<WorkflowTransition> findByFromStageId(UUID fromStageId);

    boolean existsByTemplateIdAndFromStageIdAndToStageId(UUID templateId, UUID fromStageId, UUID toStageId);

    Optional<WorkflowTransition> findByTemplateIdAndFromStageIdAndToStageId(
            UUID templateId, UUID fromStageId, UUID toStageId);
}
