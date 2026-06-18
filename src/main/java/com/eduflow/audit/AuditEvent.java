package com.eduflow.audit;

import com.eduflow.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * An immutable audit event that records a state-changing action performed by a user.
 *
 * <p>This table is <strong>append-only</strong> — records must never be updated or deleted.
 * Use {@link AuditService} to create events; never construct and save directly.</p>
 *
 * <p>{@code createdAt} (inherited from {@link BaseEntity}) serves as the event timestamp.</p>
 */
@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent extends BaseEntity {

    /** The tenant in whose context the action occurred. Null for super-admin cross-tenant actions. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** The staff user who triggered the action. Null for system-generated events. */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * The business action performed (e.g. {@code STUDENT_CREATED}, {@code DOCUMENT_APPROVED}).
     * Use constants from {@link AuditAction}.
     */
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    /** The domain entity affected (e.g. {@code STUDENT}, {@code DOCUMENT}). */
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    /** The primary key UUID of the affected entity record. */
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /**
     * Previous state as a plain string or JSON fragment.
     * Null for creation events (there is no previous state).
     */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /**
     * New state as a plain string or JSON fragment.
     * Null for deletion/deactivation events.
     */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;
}

