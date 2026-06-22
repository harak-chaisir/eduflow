package com.eduflow.audit;

/**
 * Constants for the {@code action} field of {@link AuditEvent}.
 *
 * <p>Group constants by entity type for readability.
 * New actions should be added here rather than scattered as inline strings.</p>
 */
public final class AuditAction {

    private AuditAction() {}

    // ── Tenant ────────────────────────────────────────────────────────────────
    public static final String TENANT_CREATED          = "TENANT_CREATED";
    public static final String TENANT_UPDATED          = "TENANT_UPDATED";
    public static final String TENANT_STATUS_CHANGED   = "TENANT_STATUS_CHANGED";
    public static final String TENANT_PLAN_CHANGED     = "TENANT_PLAN_CHANGED";
    public static final String TENANT_SETTINGS_UPDATED = "TENANT_SETTINGS_UPDATED";
    public static final String TENANT_ADMIN_INVITED    = "TENANT_ADMIN_INVITED";

    // ── User / Staff ────────────────────────────────────────────────────────────
    public static final String USER_PASSWORD_RESET     = "USER_PASSWORD_RESET";
    public static final String STAFF_INVITED           = "STAFF_INVITED";
    public static final String STAFF_UPDATED           = "STAFF_UPDATED";
    public static final String STAFF_STATUS_CHANGED    = "STAFF_STATUS_CHANGED";
    public static final String STAFF_PASSWORD_RESET    = "STAFF_PASSWORD_RESET";

    // ── Student ───────────────────────────────────────────────────────────────
    public static final String STUDENT_CREATED        = "STUDENT_CREATED";
    public static final String STUDENT_UPDATED        = "STUDENT_UPDATED";
    public static final String STUDENT_STATUS_CHANGED = "STUDENT_STATUS_CHANGED";
    public static final String STUDENT_DELETED        = "STUDENT_DELETED";

    // ── Document ──────────────────────────────────────────────────────────────
    public static final String DOCUMENT_UPLOADED      = "DOCUMENT_UPLOADED";
    public static final String DOCUMENT_STATUS_CHANGED = "DOCUMENT_STATUS_CHANGED";
    public static final String DOCUMENT_VERIFIED      = "DOCUMENT_VERIFIED";
    public static final String DOCUMENT_RESUBMITTED   = "DOCUMENT_RESUBMITTED";
    public static final String DOCUMENT_DELETED       = "DOCUMENT_DELETED";

    // ── University / Course (master data) ───────────────────────────────────────
    public static final String UNIVERSITY_CREATED     = "UNIVERSITY_CREATED";
    public static final String UNIVERSITY_UPDATED     = "UNIVERSITY_UPDATED";
    public static final String COURSE_CREATED         = "COURSE_CREATED";
    public static final String COURSE_UPDATED         = "COURSE_UPDATED";

    // ── Application ───────────────────────────────────────────────────────────
    public static final String APPLICATION_CREATED    = "APPLICATION_CREATED";
    public static final String APPLICATION_STATUS_CHANGED = "APPLICATION_STATUS_CHANGED";

    // ── Visa ──────────────────────────────────────────────────────────────────
    public static final String VISA_CREATED           = "VISA_CREATED";
    public static final String VISA_STATUS_CHANGED    = "VISA_STATUS_CHANGED";

    // ── Workflow definition ─────────────────────────────────────────────────────
    public static final String WORKFLOW_CREATED        = "WORKFLOW_CREATED";
    public static final String WORKFLOW_UPDATED        = "WORKFLOW_UPDATED";
    public static final String WORKFLOW_CLONED         = "WORKFLOW_CLONED";
    public static final String WORKFLOW_DEACTIVATED    = "WORKFLOW_DEACTIVATED";
    public static final String WORKFLOW_ARCHIVED       = "WORKFLOW_ARCHIVED";
    public static final String WORKFLOW_SET_DEFAULT    = "WORKFLOW_SET_DEFAULT";
    public static final String WORKFLOW_STAGE_SAVED    = "WORKFLOW_STAGE_SAVED";
    public static final String WORKFLOW_STAGE_DELETED  = "WORKFLOW_STAGE_DELETED";
    public static final String WORKFLOW_TRANSITION_SAVED   = "WORKFLOW_TRANSITION_SAVED";
    public static final String WORKFLOW_TRANSITION_DELETED = "WORKFLOW_TRANSITION_DELETED";

    // ── Workflow execution (student instances) ───────────────────────────────────
    public static final String WORKFLOW_ASSIGNED       = "WORKFLOW_ASSIGNED";
    public static final String WORKFLOW_STAGE_CHANGED  = "WORKFLOW_STAGE_CHANGED";
    public static final String WORKFLOW_COMPLETED      = "WORKFLOW_COMPLETED";

    // ── Task ──────────────────────────────────────────────────────────────────
    public static final String TASK_CREATED           = "TASK_CREATED";
    public static final String TASK_ASSIGNED          = "TASK_ASSIGNED";
    public static final String TASK_STATUS_CHANGED    = "TASK_STATUS_CHANGED";
    public static final String TASK_COMPLETED         = "TASK_COMPLETED";

    // ── Notification ────────────────────────────────────────────────────────────
    public static final String NOTIFICATION_DISPATCHED = "NOTIFICATION_DISPATCHED";
}

