package com.eduflow.user;

/**
 * Thrown when an operation would leave a tenant without any active administrator —
 * e.g. deactivating, or removing the {@code ROLE_TENANT_ADMIN} role from, the last
 * active tenant admin. A tenant must always retain at least one active admin.
 */
public class LastTenantAdminException extends RuntimeException {

    public LastTenantAdminException() {
        super("This is the tenant's last active administrator — assign another admin first");
    }
}
