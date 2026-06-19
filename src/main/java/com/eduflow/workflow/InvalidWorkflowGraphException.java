package com.eduflow.workflow;

/**
 * Thrown when a workflow's stage/transition graph is invalid (PRD FR-4 "Workflow graph
 * valid", "No invalid loops") — e.g. a duplicate or self transition, a transition that
 * crosses templates, no terminal {@code FINAL_STAGE}, or an unreachable stage.
 */
public class InvalidWorkflowGraphException extends RuntimeException {

    public InvalidWorkflowGraphException(String message) {
        super(message);
    }
}
