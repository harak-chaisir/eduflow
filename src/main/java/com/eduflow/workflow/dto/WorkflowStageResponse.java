package com.eduflow.workflow.dto;

import com.eduflow.document.DocumentType;
import com.eduflow.workflow.StageType;
import com.eduflow.workflow.WorkflowStage;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable view of a {@link WorkflowStage}.
 */
@Value
@Builder
public class WorkflowStageResponse {

    UUID id;
    String name;
    String code;
    int displayOrder;
    String description;
    String color;
    boolean active;
    Integer slaDays;
    StageType stageType;
    String ownerRole;
    List<DocumentType> requiredDocuments;

    public static WorkflowStageResponse from(WorkflowStage stage) {
        return WorkflowStageResponse.builder()
                .id(stage.getId())
                .name(stage.getName())
                .code(stage.getCode())
                .displayOrder(stage.getDisplayOrder())
                .description(stage.getDescription())
                .color(stage.getColor())
                .active(stage.isActive())
                .slaDays(stage.getSlaDays())
                .stageType(stage.getStageType())
                .ownerRole(stage.getOwnerRole())
                .requiredDocuments(stage.getRequiredDocuments() != null
                        ? new ArrayList<>(stage.getRequiredDocuments())
                        : new ArrayList<>())
                .build();
    }
}
