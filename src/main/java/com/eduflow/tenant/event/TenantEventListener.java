package com.eduflow.tenant.event;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Audit wiring for tenant domain events.
 *
 * <p>Audit listeners run synchronously with {@link EventListener} so the audit row
 * commits atomically with the change. Side-effecting consumers (the first-admin
 * invite, future billing sync) run {@code AFTER_COMMIT} and live in their own
 * modules — see {@code com.eduflow.user.TenantAdminInviteListener}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantEventListener {

    private final AuditService auditService;

    @EventListener
    public void onCreated(TenantEvents.TenantCreated e) {
        auditService.publish(e.tenantId(), e.actorUserId(),
                AuditAction.TENANT_CREATED, "TENANT", e.tenantId());
    }

    @EventListener
    public void onStatusChanged(TenantEvents.TenantStatusChanged e) {
        auditService.publish(e.tenantId(), e.actorUserId(),
                AuditAction.TENANT_STATUS_CHANGED, "TENANT", e.tenantId(),
                e.previousStatus().name(), e.newStatus().name());
    }

    @EventListener
    public void onPlanChanged(TenantEvents.TenantPlanChanged e) {
        auditService.publish(e.tenantId(), e.actorUserId(),
                AuditAction.TENANT_PLAN_CHANGED, "TENANT", e.tenantId(),
                e.previousPlan().name(), e.newPlan().name());
    }

    @EventListener
    public void onAdminInvited(TenantEvents.TenantAdminInvited e) {
        auditService.publish(e.tenantId(), e.actorUserId(),
                AuditAction.TENANT_ADMIN_INVITED, "TENANT", e.tenantId());
    }
}
