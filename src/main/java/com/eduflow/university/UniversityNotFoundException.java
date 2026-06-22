package com.eduflow.university;

import java.util.UUID;

/**
 * Thrown when a requested {@link University} cannot be found for the given tenant.
 */
public class UniversityNotFoundException extends RuntimeException {

    public UniversityNotFoundException(UUID universityId) {
        super("University not found with id: " + universityId);
    }
}
