package com.eduflow.document.storage;

import com.eduflow.document.DocumentCategory;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Storage abstraction for document file bytes. Implementations persist the bytes to a
 * backend (local filesystem by default, Google Drive when enabled) and return an opaque
 * {@link StoredFile#storageKey()} that the document record stores and later uses to
 * download or replace the content.
 *
 * <p>The service layer never talks to a backend directly — only through this port — so
 * the backend can be swapped by configuration without touching business logic.</p>
 */
public interface DocumentStoragePort {

    /** Stores a newly uploaded file and returns its storage reference. */
    StoredFile store(StorageContext context, MultipartFile file);

    /** Replaces the content behind an existing {@code storageKey} in place (revision flow). */
    StoredFile replace(String storageKey, StorageContext context, MultipartFile file);

    /** Opens the stored content for download. */
    Resource load(String storageKey);

    /** Best-effort removal of stored content; must not throw if already absent. */
    void delete(String storageKey);

    /**
     * Identifies where a file belongs in the per-student folder tree.
     *
     * @param tenantId          owning consultancy
     * @param studentId         owning student
     * @param studentFolderName human-readable student folder name, e.g. {@code "Jane Doe (uuid)"}
     * @param category          the document category (sub-folder)
     */
    record StorageContext(UUID tenantId, UUID studentId, String studentFolderName,
                          DocumentCategory category) {}

    /**
     * Result of a store/replace operation.
     *
     * @param storageKey opaque backend reference (local relative path or Drive file id)
     * @param sizeBytes  stored size in bytes
     * @param mimeType   resolved MIME type
     * @param viewLink   optional externally viewable link (Drive web view link; null for local)
     */
    record StoredFile(String storageKey, long sizeBytes, String mimeType, String viewLink) {}
}
