package com.eduflow.student;

import java.util.UUID;

/**
 * Thrown when a requested {@link Student} cannot be found for the given tenant.
 */
public class StudentNotFoundException extends RuntimeException {

    public StudentNotFoundException(UUID studentId) {
        super("Student not found with id: " + studentId);
    }

    public StudentNotFoundException(String message) {
        super(message);
    }
}

