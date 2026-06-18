package com.eduflow.document;

/**
 * Thrown when an invalid {@link DocumentStatus} transition is requested.
 *
 * <p>For example, attempting to move directly from {@code APPROVED} to {@code PENDING}.</p>
 */
public class InvalidDocumentStatusTransitionException extends RuntimeException {

    public InvalidDocumentStatusTransitionException(DocumentStatus from, DocumentStatus to) {
        super("Cannot transition document status from " + from + " to " + to);
    }
}

