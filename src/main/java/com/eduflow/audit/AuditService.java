package com.eduflow.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for recording immutable {@link AuditEvent} entries.
 *
 * <p>Use {@link Propagation#REQUIRES_NEW} so that an audit write never rolls back
 * with its parent transaction — ensuring the event is persisted even if the
 * business operation partially fails later in the same transaction.</p>
 *
 * <p>Callers should not catch exceptions from this service; audit failures are
 * logged as errors but must not swallow meaningful business exceptions.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Records an audit event without old/new value details.
     * Suitable for creation and deletion events where a snapshot is not needed.
     *
     * @param tenantId   the acting tenant (may be null for super-admin actions)
     * @param userId     the acting user (may be null for system events)
     * @param action     action constant from {@link AuditAction}
     * @param entityType domain entity type string (e.g. {@code "STUDENT"})
     * @param entityId   primary key of the affected entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(UUID tenantId, UUID userId,
                        String action, String entityType, UUID entityId) {
        publish(tenantId, userId, action, entityType, entityId, null, null);
    }

    /**
     * Records an audit event including before and after state.
     * Suitable for status-change and update events.
     *
     * @param tenantId   the acting tenant (may be null for super-admin actions)
     * @param userId     the acting user (may be null for system events)
     * @param action     action constant from {@link AuditAction}
     * @param entityType domain entity type string (e.g. {@code "STUDENT"})
     * @param entityId   primary key of the affected entity
     * @param oldValue   string/JSON representation of the previous state
     * @param newValue   string/JSON representation of the new state
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(UUID tenantId, UUID userId,
                        String action, String entityType, UUID entityId,
                        String oldValue, String newValue) {

        AuditEvent event = AuditEvent.builder()
                .tenantId(tenantId)
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();

        auditEventRepository.save(event);
        log.debug("Audit recorded: action={} entityType={} entityId={} tenant={} user={}",
                action, entityType, entityId, tenantId, userId);
    }
}


