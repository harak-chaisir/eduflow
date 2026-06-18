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

    // ── Application ───────────────────────────────────────────────────────────
    public static final String APPLICATION_CREATED    = "APPLICATION_CREATED";
    public static final String APPLICATION_STATUS_CHANGED = "APPLICATION_STATUS_CHANGED";

    // ── Visa ──────────────────────────────────────────────────────────────────
    public static final String VISA_CREATED           = "VISA_CREATED";
    public static final String VISA_STATUS_CHANGED    = "VISA_STATUS_CHANGED";
}

