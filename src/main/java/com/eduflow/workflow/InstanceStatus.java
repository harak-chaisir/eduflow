package com.eduflow.workflow;

/**
 * Lifecycle of a {@link StudentWorkflow} instance.
 *
 * <p>Stored as {@code VARCHAR}; names are pinned by {@code chk_student_workflows_status}.
 * An instance becomes {@link #COMPLETED} when it reaches a {@code FINAL_STAGE}.</p>
 */
public enum InstanceStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}
