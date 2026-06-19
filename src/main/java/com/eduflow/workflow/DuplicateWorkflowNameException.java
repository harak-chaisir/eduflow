package com.eduflow.workflow;

/**
 * Thrown when creating a workflow template whose name already exists in the tenant
 * (PRD FR-1 "Unique name validation").
 */
public class DuplicateWorkflowNameException extends RuntimeException {

    public DuplicateWorkflowNameException(String name) {
        super("A workflow named '" + name + "' already exists in this tenant");
    }
}
