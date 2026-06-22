package com.eduflow.application;

/**
 * Thrown when an invalid {@link ApplicationStatus} transition is requested,
 * e.g. moving directly from {@code DRAFT} to {@code UNCONDITIONAL_OFFER}.
 */
public class InvalidApplicationStatusTransitionException extends RuntimeException {

    public InvalidApplicationStatusTransitionException(ApplicationStatus from, ApplicationStatus to) {
        super("Cannot transition application status from " + from + " to " + to);
    }
}
