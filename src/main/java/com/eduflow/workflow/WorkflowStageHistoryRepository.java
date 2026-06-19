package com.eduflow.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link WorkflowStageHistory}. All finders are tenant-scoped.
 */
public interface WorkflowStageHistoryRepository extends JpaRepository<WorkflowStageHistory, UUID> {

    List<WorkflowStageHistory> findByStudentWorkflowIdOrderByEnteredAtAsc(UUID studentWorkflowId);

    /** The currently open history row (not yet exited) for an instance. */
    Optional<WorkflowStageHistory> findFirstByStudentWorkflowIdAndExitedAtIsNull(UUID studentWorkflowId);
}
