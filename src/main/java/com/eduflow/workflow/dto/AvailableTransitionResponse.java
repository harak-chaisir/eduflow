package com.eduflow.workflow.dto;

import com.eduflow.workflow.TransitionType;
import com.eduflow.workflow.WorkflowTransition;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * A move a student's workflow instance is currently allowed to make — the outbound
 * transitions from the current stage, surfaced to the move-stage control.
 */
@Value
@Builder
public class AvailableTransitionResponse {

    UUID toStageId;
    String toStageName;
    TransitionType transitionType;
    boolean requiresApproval;
    String label;

    public static AvailableTransitionResponse from(WorkflowTransition t) {
        return AvailableTransitionResponse.builder()
                .toStageId(t.getToStage().getId())
                .toStageName(t.getToStage().getName())
                .transitionType(t.getTransitionType())
                .requiresApproval(t.isRequiresApproval())
                .label(t.getLabel())
                .build();
    }
}
