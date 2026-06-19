package com.eduflow.workflow;

/**
 * The nature of a {@link WorkflowTransition} between two stages (PRD §9).
 *
 * <p>Stored as {@code VARCHAR}; names are pinned by {@code chk_workflow_transitions_type}.
 * {@link #MANUAL_APPROVAL} transitions (and any transition with {@code requiresApproval})
 * may only be performed by the source stage's owner role.</p>
 */
public enum TransitionType {
    FORWARD,
    BACKWARD,
    CONDITIONAL,
    MANUAL_APPROVAL
}
