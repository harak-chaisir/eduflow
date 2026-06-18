package com.eduflow.tenant;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Operator-tunable, non-identifying configuration for a tenant, kept 1:1 with
 * {@link Tenant} so the hot {@code tenants} row stays lean and the settings
 * surface can grow without churning the core table.
 *
 * <p>The primary key is shared with the owning tenant via {@link MapsId}, so
 * {@code tenant_id} is both PK and FK. This entity does not extend
 * {@code BaseEntity} (which owns a generated {@code id}); the audit columns are
 * declared directly and populated by the JPA auditing listener.</p>
 */
@Entity
@Table(name = "tenant_settings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSettings implements Serializable {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    /** UI accent colour for light white-labelling. */
    @Column(name = "brand_color", length = 20)
    private String brandColor;

    /** Drive/asset reference for the tenant logo. */
    @Column(name = "logo_reference", length = 255)
    private String logoReference;

    /** Comma-separated channels enabled for triggers (e.g. {@code EMAIL,SMS}). */
    @Column(name = "default_notification_channels", nullable = false, length = 100)
    @Builder.Default
    private String defaultNotificationChannels = "EMAIL";

    /** Workflow applied to new students; {@code null} = platform default. */
    @Column(name = "default_workflow_template_id")
    private UUID defaultWorkflowTemplateId;

    /** Tenant-specific required-document set; {@code null} falls back to the enum default. */
    @Column(name = "required_documents_override", columnDefinition = "text")
    private String requiredDocumentsOverride;

    // ── Audit ──────────────────────────────────────────────────────────────────

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
}
