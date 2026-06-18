package com.eduflow.document;

import com.eduflow.common.BaseEntity;
import com.eduflow.student.Student;
import com.eduflow.tenant.Tenant;
import com.eduflow.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Represents a document submitted by a student as part of the application process.
 *
 * <p>Actual file bytes are stored in Google Drive; only the Drive metadata (file ID,
 * folder ID) and verification state are persisted here.</p>
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 *
 * <p>Status transitions are enforced in {@code DocumentService}:
 * {@code PENDING → APPROVED | REJECTED | NEEDS_REVISION}.</p>
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document extends BaseEntity {

    // ── Tenant scope ─────────────────────────────────────────────────────────

    /** The consultancy this document belongs to. Never null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // ── Student ──────────────────────────────────────────────────────────────

    /** The student this document belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    // ── Document metadata ────────────────────────────────────────────────────

    /** The catalogue type of the document. */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 100)
    private DocumentType documentType;

    /** Category, denormalised from {@link DocumentType#category()} for querying/grouping. */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_category", nullable = false, length = 50)
    private DocumentCategory documentCategory;

    /** Original filename as provided by the uploader. */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    /** MIME type captured at upload time. */
    @Column(name = "mime_type", length = 150)
    private String mimeType;

    /** File size in bytes. */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /**
     * Opaque storage reference returned by the active {@code DocumentStoragePort}
     * (a relative path for the local adapter, a Drive file id for the Drive adapter).
     */
    @Column(name = "storage_key", length = 512)
    private String storageKey;

    /** Optional free-text description supplied by the uploader. */
    @Column(name = "description", length = 500)
    private String description;

    /** Increments each time the file is resubmitted in place. Starts at 1. */
    @Column(name = "revision_number", nullable = false)
    @Builder.Default
    private int revisionNumber = 1;

    // ── Legacy Google Drive metadata (retained; populated only by the Drive adapter) ──

    /** Google Drive file ID; null unless the Drive storage adapter is active. */
    @Column(name = "drive_file_id", length = 255)
    private String driveFileId;

    /** Google Drive folder ID for the student's document folder. */
    @Column(name = "drive_folder_id", length = 255)
    private String driveFolderId;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Current review status. Defaults to {@link DocumentStatus#PENDING}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    // ── Review outcome ───────────────────────────────────────────────────────

    /** Free-text reviewer notes visible to counselors and students. */
    @Column(name = "notes", length = 1000)
    private String notes;

    /**
     * Reason provided when the document is rejected or needs revision.
     * Required in the service layer when setting {@link DocumentStatus#REJECTED}
     * or {@link DocumentStatus#NEEDS_REVISION}.
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** The documentation officer who last reviewed this document. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id")
    private User verifiedBy;

    /** Timestamp of the last verification action (approve, reject, or needs-revision). */
    @Column(name = "verified_at")
    private Instant verifiedAt;
}

