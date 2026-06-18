package com.eduflow.document.dto;

import com.eduflow.document.Document;
import com.eduflow.document.DocumentCategory;
import com.eduflow.document.DocumentStatus;
import com.eduflow.document.DocumentType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable response representation of a {@link Document}.
 *
 * <p>Never exposes credentials or storage tokens; the {@code downloadUrl} points at the
 * authenticated proxy endpoint, not the backend.</p>
 */
@Value
@Builder
public class DocumentResponse {

    UUID id;

    // ── Scope ─────────────────────────────────────────────────────────────────
    UUID tenantId;
    UUID studentId;

    // ── Type / category ─────────────────────────────────────────────────────────
    DocumentType documentType;
    String documentTypeLabel;
    DocumentCategory documentCategory;
    boolean coreRequirement;

    // ── File metadata ───────────────────────────────────────────────────────────
    String originalFilename;
    String mimeType;
    Long fileSizeBytes;
    int revisionNumber;
    String description;
    String downloadUrl;

    // ── Status ────────────────────────────────────────────────────────────────
    DocumentStatus status;
    String rejectionReason;

    // ── Verification ──────────────────────────────────────────────────────────
    UUID verifiedById;
    String verifiedByName;
    Instant verifiedAt;

    // ── Audit ─────────────────────────────────────────────────────────────────
    Instant createdAt;
    Instant updatedAt;
    String createdBy;

    /**
     * Maps a {@link Document} entity to a {@link DocumentResponse}.
     *
     * @param doc the entity to map
     * @return a populated response DTO
     */
    public static DocumentResponse from(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .tenantId(doc.getTenant().getId())
                .studentId(doc.getStudent().getId())
                .documentType(doc.getDocumentType())
                .documentTypeLabel(doc.getDocumentType().label())
                .documentCategory(doc.getDocumentCategory())
                .coreRequirement(doc.getDocumentType().isCoreRequirement())
                .originalFilename(doc.getOriginalFilename())
                .mimeType(doc.getMimeType())
                .fileSizeBytes(doc.getFileSizeBytes())
                .revisionNumber(doc.getRevisionNumber())
                .description(doc.getDescription())
                .downloadUrl(doc.getStorageKey() != null ? "/api/v1/documents/" + doc.getId() + "/content" : null)
                .status(doc.getStatus())
                .rejectionReason(doc.getRejectionReason())
                .verifiedById(doc.getVerifiedBy() != null ? doc.getVerifiedBy().getId() : null)
                .verifiedByName(doc.getVerifiedBy() != null ? doc.getVerifiedBy().getFullName() : null)
                .verifiedAt(doc.getVerifiedAt())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .createdBy(doc.getCreatedBy())
                .build();
    }
}
