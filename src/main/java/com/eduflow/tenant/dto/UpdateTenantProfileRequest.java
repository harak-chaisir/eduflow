package com.eduflow.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for editing a tenant's profile (name, contact, locale).
 * Only non-null fields are applied. Slug, plan, and status are not editable here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantProfileRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    String name;

    @Size(max = 255)
    String primaryContactName;

    @Email(message = "Primary contact email must be valid")
    @Size(max = 255)
    String primaryContactEmail;

    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{7,30}$", message = "Phone number format is invalid")
    String primaryContactPhone;

    @Size(max = 20)
    String locale;

    @Size(max = 64)
    String timezone;
}
