package com.eduflow.tenant.dto;

import com.eduflow.tenant.TenantSettings;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable response representation of {@link TenantSettings}.
 */
@Value
@Builder
public class TenantSettingsResponse {

    UUID tenantId;
    String brandColor;
    String logoReference;
    String defaultNotificationChannels;
    UUID defaultWorkflowTemplateId;
    String requiredDocumentsOverride;
    Instant updatedAt;
    String updatedBy;

    public static TenantSettingsResponse from(TenantSettings s) {
        return TenantSettingsResponse.builder()
                .tenantId(s.getTenantId())
                .brandColor(s.getBrandColor())
                .logoReference(s.getLogoReference())
                .defaultNotificationChannels(s.getDefaultNotificationChannels())
                .defaultWorkflowTemplateId(s.getDefaultWorkflowTemplateId())
                .requiredDocumentsOverride(s.getRequiredDocumentsOverride())
                .updatedAt(s.getUpdatedAt())
                .updatedBy(s.getUpdatedBy())
                .build();
    }
}
