package com.eduflow.student;

/**
 * Lifecycle status of a student record.
 *
 * <p>Allowed forward transitions:
 * <pre>
 *   LEAD → QUALIFIED → ACTIVE → ENROLLED
 *   Any  → INACTIVE  (soft-delete / de-activation)
 * </pre>
 * </p>
 */
public enum StudentStatus {
    LEAD,
    QUALIFIED,
    ACTIVE,
    ENROLLED,
    INACTIVE
}

