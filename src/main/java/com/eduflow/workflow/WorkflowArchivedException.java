package com.eduflow.workflow;

import java.util.UUID;

/**
 * Thrown when an operation is attempted that an archived workflow template forbids —
 * notably assigning the template to a new student (PRD FR-9 "New assignments blocked").
 */
public class WorkflowArchivedException extends RuntimeException {

    public WorkflowArchivedException(UUID templateId) {
        super("Workflow template is archived and cannot accept new assignments: " + templateId);
    }
}
