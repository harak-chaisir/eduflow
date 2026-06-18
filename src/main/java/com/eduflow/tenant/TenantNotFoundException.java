package com.eduflow.tenant;

import java.util.UUID;

/**
 * Thrown when a tenant cannot be found by its UUID.
 */
public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(UUID tenantId) {
        super("Tenant not found with id: " + tenantId);
    }
}

