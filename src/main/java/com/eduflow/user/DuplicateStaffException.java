package com.eduflow.user;

/**
 * Thrown when inviting a staff member whose email already exists in the same tenant.
 */
public class DuplicateStaffException extends RuntimeException {

    public DuplicateStaffException(String email) {
        super("A staff member with email '" + email + "' already exists in this tenant");
    }
}
