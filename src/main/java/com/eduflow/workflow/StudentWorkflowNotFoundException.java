package com.eduflow.workflow;

/**
 * Thrown when a student's workflow instance cannot be found for the calling tenant.
 */
public class StudentWorkflowNotFoundException extends RuntimeException {
    public StudentWorkflowNotFoundException(String message) {
        super(message);
    }
}
