package com.eduflow.document;

import java.util.UUID;

/**
 * Thrown when a requested {@link Document} cannot be found for the given student and tenant.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID documentId) {
        super("Document not found with id: " + documentId);
    }
}

