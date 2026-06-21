package com.eduflow.user;

import java.util.UUID;

/**
 * Thrown when a requested staff {@link User} cannot be found for the calling user's tenant.
 */
public class StaffNotFoundException extends RuntimeException {

    public StaffNotFoundException(UUID userId) {
        super("Staff member not found with id: " + userId);
    }
}
