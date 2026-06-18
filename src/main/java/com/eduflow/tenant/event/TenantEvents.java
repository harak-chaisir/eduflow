package com.eduflow.tenant.event;

import com.eduflow.tenant.TenantPlan;
import com.eduflow.tenant.TenantStatus;

import java.util.UUID;

/**
 * Domain events published by {@code TenantService} so audit, the first-admin
 * invite, and (future) billing/notification concerns stay decoupled from the
 * tenant lifecycle (see PRD §13).
 */
public final class TenantEvents {

    private TenantEvents() {}

    /** A tenant was provisioned (committed). Consumers: user module (invite admin), audit. */
    public record TenantCreated(UUID tenantId, UUID actorUserId,
                                String tenantName,
                                String primaryContactName, String primaryContactEmail) {}

    /** A tenant lifecycle status changed. Consumers: audit, billing (future), notifications. */
    public record TenantStatusChanged(UUID tenantId, UUID actorUserId,
                                      TenantStatus previousStatus, TenantStatus newStatus,
                                      String reason) {}

    /** A tenant plan or limits changed. Consumers: audit, billing (future). */
    public record TenantPlanChanged(UUID tenantId, UUID actorUserId,
                                    TenantPlan previousPlan, TenantPlan newPlan) {}

    /** An additional tenant admin was requested. Consumers: user module (invite), audit. */
    public record TenantAdminInvited(UUID tenantId, UUID actorUserId, String email) {}
}
