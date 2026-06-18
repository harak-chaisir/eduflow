package com.eduflow.tenant;

/**
 * Thrown when provisioning a tenant with a slug that already exists.
 * Mapped to {@code 409 CONFLICT} by the global exception handler.
 */
public class DuplicateSlugException extends RuntimeException {

    public DuplicateSlugException(String slug) {
        super("A tenant with slug '" + slug + "' already exists");
    }
}
