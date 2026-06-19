package com.eduflow.workflow.dto;

import com.eduflow.workflow.InstanceStatus;
import com.eduflow.workflow.StudentWorkflow;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable view of a {@link StudentWorkflow} for the student detail page.
 *
 * <p>SLA fields are computed on-read from {@code currentStageEnteredAt} versus the
 * current stage's {@code slaDays} (PRD §12, FR-8). {@code availableTransitions} lists
 * the moves currently permitted from the current stage.</p>
 */
@Value
@Builder
public class StudentWorkflowResponse {

    UUID id;
    UUID studentId;
    UUID templateId;
    String templateName;
    InstanceStatus status;

    UUID currentStageId;
    String currentStageName;
    String currentStageColor;
    Integer currentStageSlaDays;

    Instant startedAt;
    Instant completedAt;
    Instant currentStageEnteredAt;

    long daysInStage;
    boolean slaBreached;

    List<AvailableTransitionResponse> availableTransitions;

    public static StudentWorkflowResponse from(StudentWorkflow w,
                                               List<AvailableTransitionResponse> transitions) {
        var stage = w.getCurrentStage();
        Integer slaDays = stage != null ? stage.getSlaDays() : null;
        long days = Duration.between(w.getCurrentStageEnteredAt(), Instant.now()).toDays();
        boolean breached = slaDays != null && days > slaDays;

        return StudentWorkflowResponse.builder()
                .id(w.getId())
                .studentId(w.getStudent().getId())
                .templateId(w.getTemplate().getId())
                .templateName(w.getTemplate().getName())
                .status(w.getStatus())
                .currentStageId(stage != null ? stage.getId() : null)
                .currentStageName(stage != null ? stage.getName() : null)
                .currentStageColor(stage != null ? stage.getColor() : null)
                .currentStageSlaDays(slaDays)
                .startedAt(w.getStartedAt())
                .completedAt(w.getCompletedAt())
                .currentStageEnteredAt(w.getCurrentStageEnteredAt())
                .daysInStage(days)
                .slaBreached(breached || w.isSlaBreached())
                .availableTransitions(transitions)
                .build();
    }
}
