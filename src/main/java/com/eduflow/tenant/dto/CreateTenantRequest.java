package com.eduflow.tenant.dto;

import com.eduflow.tenant.TenantPlan;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for provisioning a new tenant (super-admin only).
 *
 * <p>Modelled as a mutable bean so Jackson can deserialize it directly;
 * {@code @Builder} is retained for programmatic use.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {

    @NotBlank(message = "Tenant name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    String name;

    /** URL-safe, lowercase, hyphen-separated. Immutable once created. */
    @NotBlank(message = "Slug is required")
    @Size(max = 100, message = "Slug must not exceed 100 characters")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "Slug must be lowercase alphanumeric words separated by single hyphens")
    String slug;

    @NotNull(message = "Plan is required")
    TenantPlan plan;

    // ── Primary contact ────────────────────────────────────────────────────────

    @NotBlank(message = "Primary contact name is required")
    @Size(max = 255)
    String primaryContactName;

    @NotBlank(message = "Primary contact email is required")
    @Email(message = "Primary contact email must be valid")
    @Size(max = 255)
    String primaryContactEmail;

    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{7,30}$", message = "Phone number format is invalid")
    String primaryContactPhone;

    // ── Optional overrides / locale ──────────────────────────────────────────────

    @Size(max = 20)
    String locale;

    @Size(max = 64)
    String timezone;

    /** Optional override of the plan's default student cap. */
    @PositiveOrZero(message = "maxStudents must not be negative")
    Integer maxStudents;

    /** Optional override of the plan's default staff cap. */
    @PositiveOrZero(message = "maxStaffUsers must not be negative")
    Integer maxStaffUsers;
}
