package com.eduflow.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link AuditEvent}.
 *
 * <p><strong>Append-only:</strong> only {@code save} and read operations should be used.
 * Never call {@code delete} or update any record.</p>
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * Returns all audit events for a specific entity, ordered by creation time descending.
     *
     * @param entityType the domain entity type (e.g. {@code "STUDENT"})
     * @param entityId   the entity's UUID
     * @return list of events, most recent first
     */
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId);

    /**
     * Returns all audit events for a tenant, most recent first.
     * Intended for super-admin use only.
     *
     * @param tenantId the tenant UUID
     * @return list of events
     */
    List<AuditEvent> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * Returns the most recent audit events triggered by a given user within a tenant,
     * most recent first. Drives the "recent activity" feed on the staff detail page.
     *
     * @param tenantId the tenant UUID
     * @param userId   the acting user's UUID
     * @return up to 8 events, most recent first
     */
    List<AuditEvent> findTop8ByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);
}

