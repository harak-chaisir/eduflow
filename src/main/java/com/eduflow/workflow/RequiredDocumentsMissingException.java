package com.eduflow.workflow;

import com.eduflow.document.DocumentType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when a student cannot leave a stage because one or more of that stage's
 * required documents are not yet APPROVED (PRD §10, FR-5).
 */
public class RequiredDocumentsMissingException extends RuntimeException {
    public RequiredDocumentsMissingException(List<DocumentType> missing) {
        super("Required documents are missing or not yet approved: "
                + missing.stream().map(DocumentType::label).collect(Collectors.joining(", ")));
    }
}
