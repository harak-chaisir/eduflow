package com.eduflow.tenant.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for updating a tenant's settings. Only non-null fields are applied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantSettingsRequest {

    @Size(max = 20)
    String brandColor;

    @Size(max = 255)
    String logoReference;

    @Size(max = 100)
    String defaultNotificationChannels;

    UUID defaultWorkflowTemplateId;

    String requiredDocumentsOverride;
}
