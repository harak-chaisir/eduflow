package com.eduflow.workflow.dto;

import com.eduflow.workflow.TransitionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Inbound payload to define an allowed transition between two stages (PRD §9, FR-4).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTransitionRequest {

    @NotNull(message = "Source stage is required")
    private UUID fromStageId;

    @NotNull(message = "Target stage is required")
    private UUID toStageId;

    private TransitionType transitionType;

    @Size(max = 150, message = "Label must not exceed 150 characters")
    private String label;

    private Boolean requiresApproval;
}
