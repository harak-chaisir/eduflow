package com.eduflow.task;

/**
 * Relative urgency of a {@link Task}. Stored as {@code VARCHAR}; names pinned by
 * {@code chk_tasks_priority}.
 */
public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH
}
