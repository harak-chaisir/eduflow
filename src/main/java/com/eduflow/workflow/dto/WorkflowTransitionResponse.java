package com.eduflow.workflow.dto;

import com.eduflow.workflow.TransitionType;
import com.eduflow.workflow.WorkflowTransition;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Immutable view of a {@link WorkflowTransition}.
 */
@Value
@Builder
public class WorkflowTransitionResponse {

    UUID id;
    UUID fromStageId;
    String fromStageName;
    UUID toStageId;
    String toStageName;
    TransitionType transitionType;
    String label;
    boolean requiresApproval;

    public static WorkflowTransitionResponse from(WorkflowTransition t) {
        return WorkflowTransitionResponse.builder()
                .id(t.getId())
                .fromStageId(t.getFromStage().getId())
                .fromStageName(t.getFromStage().getName())
                .toStageId(t.getToStage().getId())
                .toStageName(t.getToStage().getName())
                .transitionType(t.getTransitionType())
                .label(t.getLabel())
                .requiresApproval(t.isRequiresApproval())
                .build();
    }
}
