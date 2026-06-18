package com.eduflow.tenant.dto;

import com.eduflow.tenant.TenantPlan;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for changing a tenant's plan and (optionally) overriding the
 * plan's default limits. When the overrides are null, the new plan's defaults apply.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeTenantPlanRequest {

    @NotNull(message = "Plan is required")
    TenantPlan plan;

    @PositiveOrZero(message = "maxStudents must not be negative")
    Integer maxStudents;

    @PositiveOrZero(message = "maxStaffUsers must not be negative")
    Integer maxStaffUsers;
}
