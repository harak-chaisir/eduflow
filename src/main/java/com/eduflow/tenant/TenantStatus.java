package com.eduflow.tenant;

import java.util.Set;

/**
 * Lifecycle states for a tenant (consultancy organisation).
 *
 * <pre>
 *   ACTIVE    → SUSPENDED (temporary hold) | INACTIVE (offboard)
 *   SUSPENDED → ACTIVE    (restore after hold)
 *   INACTIVE  → ACTIVE    (win-back / reactivation)
 * </pre>
 *
 * <p>{@code SUSPENDED → INACTIVE} is intentionally disallowed: an operator must
 * reactivate first, then deactivate, keeping the reason history clean. The
 * transition rules live on the enum via {@link #canTransitionTo}; the service
 * validates every change and throws {@link InvalidTenantStatusTransitionException}
 * on a violation.</p>
 */
public enum TenantStatus {

    /** Tenant is operating normally. */
    ACTIVE,
    /** Tenant has been deactivated (offboarded) but data is retained. */
    INACTIVE,
    /** Tenant has been temporarily suspended (e.g. non-payment). */
    SUSPENDED;

    /** Returns {@code true} if a tenant may move from this status to {@code target}. */
    public boolean canTransitionTo(TenantStatus target) {
        return switch (this) {
            case ACTIVE -> Set.of(SUSPENDED, INACTIVE).contains(target);
            case SUSPENDED -> target == ACTIVE;
            case INACTIVE -> target == ACTIVE;
        };
    }
}
