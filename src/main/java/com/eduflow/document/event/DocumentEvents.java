package com.eduflow.document.event;

import com.eduflow.document.DocumentStatus;
import com.eduflow.document.DocumentType;

import java.util.UUID;

/**
 * Domain events published by {@code DocumentService} so that audit and notification
 * concerns stay decoupled from the document workflow (see PRD §9).
 */
public final class DocumentEvents {

    private DocumentEvents() {}

    /** A new document was uploaded. */
    public record DocumentUploaded(UUID tenantId, UUID actorUserId, UUID studentId,
                                   UUID documentId, DocumentType type) {}

    /** A document was verified (approved / rejected / revision requested). */
    public record DocumentVerified(UUID tenantId, UUID actorUserId, UUID studentId,
                                   UUID documentId, DocumentType type,
                                   DocumentStatus previousStatus, DocumentStatus newStatus,
                                   String remarks) {}

    /** A document file was resubmitted in place. */
    public record DocumentResubmitted(UUID tenantId, UUID actorUserId, UUID studentId,
                                      UUID documentId, DocumentType type, int revisionNumber) {}

    /** A document record was deleted. */
    public record DocumentDeleted(UUID tenantId, UUID actorUserId, UUID studentId,
                                  UUID documentId, DocumentType type) {}
}
