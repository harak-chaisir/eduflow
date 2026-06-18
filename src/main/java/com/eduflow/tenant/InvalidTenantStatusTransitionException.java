package com.eduflow.tenant;

/**
 * Thrown when an attempted tenant status change is not permitted by
 * {@link TenantStatus#canTransitionTo(TenantStatus)}.
 * Mapped to {@code 422 UNPROCESSABLE_ENTITY} by the global exception handler.
 */
public class InvalidTenantStatusTransitionException extends RuntimeException {

    public InvalidTenantStatusTransitionException(TenantStatus from, TenantStatus to) {
        super("Cannot transition tenant from " + from + " to " + to);
    }
}
