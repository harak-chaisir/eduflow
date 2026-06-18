package com.eduflow.student;

/**
 * Thrown when an invalid {@link StudentStatus} transition is requested.
 *
 * <p>For example, attempting to move directly from {@code LEAD} to {@code ENROLLED}.</p>
 */
public class InvalidStudentStatusTransitionException extends RuntimeException {

    public InvalidStudentStatusTransitionException(StudentStatus from, StudentStatus to) {
        super("Cannot transition student status from " + from + " to " + to);
    }
}

