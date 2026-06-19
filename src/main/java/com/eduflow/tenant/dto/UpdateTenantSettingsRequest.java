package com.eduflow.tenant.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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

    /**
     * Selected notification channels from the workspace UI (multi-select). When present, this
     * takes precedence over {@link #defaultNotificationChannels} and is normalised to a
     * comma-separated list of {@link com.eduflow.tenant.NotificationChannel} names by the service.
     */
    List<String> notificationChannels;

    UUID defaultWorkflowTemplateId;

    String requiredDocumentsOverride;
}
