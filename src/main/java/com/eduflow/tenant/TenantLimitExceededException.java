package com.eduflow.tenant;

/**
 * Thrown when creating a resource (student or staff user) would exceed the
 * tenant's plan limit. Mapped to {@code 409 CONFLICT} by the global exception handler.
 */
public class TenantLimitExceededException extends RuntimeException {

    public TenantLimitExceededException(String resource, int limit) {
        super("Plan limit reached: this workspace allows at most " + limit + " " + resource
                + ". Upgrade the plan to add more.");
    }
}
