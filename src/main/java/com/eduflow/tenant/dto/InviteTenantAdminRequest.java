package com.eduflow.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for inviting an additional {@code TENANT_ADMIN} to a tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteTenantAdminRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255)
    String email;

    @Size(max = 100)
    String firstName;

    @Size(max = 100)
    String lastName;
}
