package com.eduflow.tenant.dto;

import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantPlan;
import com.eduflow.tenant.TenantStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable response representation of a {@link Tenant}.
 * Never exposes credentials or internal-only fields.
 */
@Value
@Builder
public class TenantResponse {

    UUID id;
    String name;
    String slug;
    TenantStatus status;

    // ── Plan / limits ──────────────────────────────────────────────────────────
    TenantPlan plan;
    Integer maxStudents;
    Integer maxStaffUsers;

    // ── Primary contact ────────────────────────────────────────────────────────
    String primaryContactName;
    String primaryContactEmail;
    String primaryContactPhone;

    // ── Storage / locale ───────────────────────────────────────────────────────
    String driveRootFolderId;
    String locale;
    String timezone;

    // ── Lifecycle stamps ───────────────────────────────────────────────────────
    Instant suspendedAt;
    String suspensionReason;
    Instant deactivatedAt;

    // ── Audit ──────────────────────────────────────────────────────────────────
    Instant createdAt;
    Instant updatedAt;
    String createdBy;

    public static TenantResponse from(Tenant t) {
        return TenantResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .slug(t.getSlug())
                .status(t.getStatus())
                .plan(t.getPlan())
                .maxStudents(t.getMaxStudents())
                .maxStaffUsers(t.getMaxStaffUsers())
                .primaryContactName(t.getPrimaryContactName())
                .primaryContactEmail(t.getPrimaryContactEmail())
                .primaryContactPhone(t.getPrimaryContactPhone())
                .driveRootFolderId(t.getDriveRootFolderId())
                .locale(t.getLocale())
                .timezone(t.getTimezone())
                .suspendedAt(t.getSuspendedAt())
                .suspensionReason(t.getSuspensionReason())
                .deactivatedAt(t.getDeactivatedAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .createdBy(t.getCreatedBy())
                .build();
    }
}
