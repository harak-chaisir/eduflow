package com.eduflow.workflow.dto;

import com.eduflow.workflow.WorkflowTemplate;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable view of a {@link WorkflowTemplate}.
 *
 * <p>{@link #from(WorkflowTemplate)} returns the header only (cheap for list views);
 * {@link #withGraph(WorkflowTemplate)} additionally materialises the stage and
 * transition collections for the builder/detail view.</p>
 */
@Value
@Builder
public class WorkflowTemplateResponse {

    UUID id;
    String name;
    String description;
    String country;
    int version;
    boolean active;
    boolean defaultTemplate;
    boolean archived;
    int stageCount;

    List<WorkflowStageResponse> stages;
    List<WorkflowTransitionResponse> transitions;

    UUID tenantId;
    Instant createdAt;
    Instant updatedAt;
    String createdBy;

    /** Header-only view; stages/transitions are left null to avoid lazy loading. */
    public static WorkflowTemplateResponse from(WorkflowTemplate t) {
        return base(t).build();
    }

    /** Full view including the stage + transition graph (detail/builder). */
    public static WorkflowTemplateResponse withGraph(WorkflowTemplate t) {
        return base(t)
                .stages(t.getStages().stream().map(WorkflowStageResponse::from).toList())
                .transitions(t.getTransitions().stream().map(WorkflowTransitionResponse::from).toList())
                .build();
    }

    private static WorkflowTemplateResponseBuilder base(WorkflowTemplate t) {
        return WorkflowTemplateResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .description(t.getDescription())
                .country(t.getCountry())
                .version(t.getVersion())
                .active(t.isActive())
                .defaultTemplate(t.isDefaultTemplate())
                .archived(t.isArchived())
                .stageCount(t.getStages() != null ? t.getStages().size() : 0)
                .tenantId(t.getTenant().getId())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .createdBy(t.getCreatedBy());
    }
}
