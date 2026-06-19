package com.eduflow.workflow.event;

import java.util.UUID;

/**
 * Domain events published by {@code StudentWorkflowService} so task generation,
 * notifications (PRD §13), and SLA concerns stay decoupled from stage movement.
 */
public final class WorkflowEvents {

    private WorkflowEvents() {}

    /**
     * A student's workflow entered a stage. Consumers: task module (generate the stage's
     * owner-role task), notifications. {@code initial} is true for the entry stage created
     * at assignment time.
     */
    public record StageEntered(UUID tenantId, UUID actorUserId, UUID instanceId,
                               UUID studentId, UUID stageId, boolean initial) {}

    /** A workflow reached a FINAL_STAGE and completed. Consumers: notifications, analytics. */
    public record WorkflowCompleted(UUID tenantId, UUID actorUserId, UUID instanceId, UUID studentId) {}

    /** An SLA breach was detected for an instance's current stage. Consumers: notifications. */
    public record SlaBreached(UUID tenantId, UUID instanceId, UUID studentId, UUID stageId) {}
}
