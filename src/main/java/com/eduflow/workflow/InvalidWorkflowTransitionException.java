package com.eduflow.workflow;

/**
 * Thrown when a requested stage move is not permitted — either no transition connects
 * the current stage to the target (PRD FR-7 "Transition validated"), or the caller
 * lacks the owner role required by an approval transition.
 */
public class InvalidWorkflowTransitionException extends RuntimeException {
    public InvalidWorkflowTransitionException(String message) {
        super(message);
    }
}
