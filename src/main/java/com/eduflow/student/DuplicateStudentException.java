package com.eduflow.student;

/**
 * Thrown when attempting to register a student whose email already exists in the same tenant.
 */
public class DuplicateStudentException extends RuntimeException {

    public DuplicateStudentException(String email) {
        super("A student with email '" + email + "' already exists in this tenant");
    }
}

