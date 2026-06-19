package com.eduflow.task;

/**
 * Lifecycle of a {@link Task} (PRD §7.7).
 *
 * <p>Allowed transitions: {@code PENDING → IN_PROGRESS → COMPLETED}; any non-terminal
 * status may move to {@code CANCELLED}. Stored as {@code VARCHAR}; names pinned by
 * {@code chk_tasks_status}.</p>
 */
public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
