package com.eduflow.tenant.dto;

import com.eduflow.tenant.TenantStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for changing a tenant's lifecycle status.
 * {@code reason} is required when moving to {@code SUSPENDED} (validated in the service).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeTenantStatusRequest {

    @NotNull(message = "Target status is required")
    TenantStatus status;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    String reason;
}
