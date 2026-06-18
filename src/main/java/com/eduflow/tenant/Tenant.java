package com.eduflow.tenant;

import com.eduflow.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Represents an education consultancy (tenant) on the EduFlow platform.
 * Every piece of business data is scoped to a tenant.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    /** Human-readable name of the consultancy (e.g. "ABC Education"). */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** URL-safe unique slug used to identify the tenant in URLs (e.g. "abc-education"). Immutable after creation. */
    @Column(name = "slug", nullable = false, unique = true, length = 100, updatable = false)
    private String slug;

    /** Lifecycle status of the tenant. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    // ── Commercial / plan ──────────────────────────────────────────────────────

    /** Subscription tier. */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 50)
    @Builder.Default
    private TenantPlan plan = TenantPlan.STARTER;

    /** Max students allowed; {@code null} = unlimited. */
    @Column(name = "max_students")
    private Integer maxStudents;

    /** Max staff users allowed; {@code null} = unlimited. */
    @Column(name = "max_staff_users")
    private Integer maxStaffUsers;

    // ── Primary contact ────────────────────────────────────────────────────────

    @Column(name = "primary_contact_name", length = 255)
    private String primaryContactName;

    @Column(name = "primary_contact_email", length = 255)
    private String primaryContactEmail;

    @Column(name = "primary_contact_phone", length = 50)
    private String primaryContactPhone;

    // ── Storage / locale ───────────────────────────────────────────────────────

    /** Optional per-tenant Drive root; {@code null} falls back to the global app root. */
    @Column(name = "drive_root_folder_id", length = 255)
    private String driveRootFolderId;

    @Column(name = "locale", nullable = false, length = 20)
    @Builder.Default
    private String locale = "en-NP";

    @Column(name = "timezone", nullable = false, length = 64)
    @Builder.Default
    private String timezone = "Asia/Kathmandu";

    // ── Lifecycle stamps ───────────────────────────────────────────────────────

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;
}
