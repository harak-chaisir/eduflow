package com.eduflow.notification;

/**
 * The workflow events that generate notifications (PRD §13). Stored as {@code VARCHAR};
 * names pinned by {@code chk_notifications_type}.
 */
public enum NotificationType {
    STAGE_ENTERED,
    STAGE_COMPLETED,
    DOCUMENT_MISSING,
    SLA_BREACHED,
    TASK_ASSIGNED,
    TASK_COMPLETED
}
