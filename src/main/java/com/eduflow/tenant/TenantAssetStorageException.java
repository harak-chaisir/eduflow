package com.eduflow.tenant;

/** Raised when a tenant asset (e.g. logo) cannot be stored or read. */
public class TenantAssetStorageException extends RuntimeException {

    public TenantAssetStorageException(String message) {
        super(message);
    }

    public TenantAssetStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
