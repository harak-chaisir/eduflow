package com.eduflow.workflow;

/**
 * Classifies a {@link WorkflowStage} by the kind of work it represents (PRD §8).
 *
 * <p>Stored as {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)}; the names are
 * pinned by the {@code chk_workflow_stages_type} constraint, so do not rename.
 * {@link #FINAL_STAGE} marks a terminal stage — reaching it completes the instance.</p>
 */
public enum StageType {
    NORMAL,
    DOCUMENT_STAGE,
    APPLICATION_STAGE,
    VISA_STAGE,
    DECISION_STAGE,
    FINAL_STAGE
}
