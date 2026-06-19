package com.eduflow.workflow;

import java.util.UUID;

/**
 * Thrown when a requested {@link WorkflowTemplate} (or one of its stages/transitions)
 * cannot be found for the calling user's tenant.
 */
public class WorkflowTemplateNotFoundException extends RuntimeException {

    public WorkflowTemplateNotFoundException(UUID id) {
        super("Workflow template not found with id: " + id);
    }

    public WorkflowTemplateNotFoundException(String message) {
        super(message);
    }
}
