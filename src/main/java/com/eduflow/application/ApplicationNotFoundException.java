package com.eduflow.application;

import java.util.UUID;

/**
 * Thrown when a requested {@link Application} cannot be found for the given tenant.
 */
public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(UUID applicationId) {
        super("Application not found with id: " + applicationId);
    }
}
