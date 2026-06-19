package com.eduflow.task.dto;

import com.eduflow.task.Task;
import com.eduflow.task.TaskPriority;
import com.eduflow.task.TaskStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable view of a {@link Task} for list/detail rendering.
 */
@Value
@Builder
public class TaskResponse {

    UUID id;
    UUID studentId;
    String studentName;
    UUID studentWorkflowId;
    UUID stageId;
    String title;
    String description;
    String assignedRole;
    UUID assignedUserId;
    TaskStatus status;
    TaskPriority priority;
    Instant dueAt;
    boolean overdue;
    Instant completedAt;

    public static TaskResponse from(Task t) {
        boolean overdue = t.getDueAt() != null
                && (t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.IN_PROGRESS)
                && t.getDueAt().isBefore(Instant.now());
        return TaskResponse.builder()
                .id(t.getId())
                .studentId(t.getStudent().getId())
                .studentName(t.getStudent().getFullName())
                .studentWorkflowId(t.getStudentWorkflowId())
                .stageId(t.getStageId())
                .title(t.getTitle())
                .description(t.getDescription())
                .assignedRole(t.getAssignedRole())
                .assignedUserId(t.getAssignedUserId())
                .status(t.getStatus())
                .priority(t.getPriority())
                .dueAt(t.getDueAt())
                .overdue(overdue)
                .completedAt(t.getCompletedAt())
                .build();
    }
}
