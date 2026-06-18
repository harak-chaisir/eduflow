package com.eduflow.document.storage;

import com.eduflow.config.GoogleDriveProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Google Drive {@link DocumentStoragePort}, selected when
 * {@code eduflow.google-drive.enabled=true}.
 *
 * <p><b>Seam, not yet wired to the Drive SDK.</b> To keep the default build verifiable on
 * the current stack, the {@code google-api-services-drive} client libraries are not bundled.
 * To complete this adapter:</p>
 * <ol>
 *   <li>Add the Drive client dependencies from {@code docs/INTEGRATION.md} §2 to {@code pom.xml}.</li>
 *   <li>Build the authenticated {@code Drive} client from {@link GoogleDriveProperties}
 *       (service-account key at {@code credentials-location}, scope {@code drive.file}).</li>
 *   <li>Implement provisioning of the per-student folder tree (cached in
 *       {@code student_drive_folders}) and stream uploads via {@code InputStreamContent}
 *       with {@code supportsAllDrives=true}.</li>
 * </ol>
 * Until then, enabling Drive fails fast with a clear message rather than silently misbehaving.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "eduflow.google-drive.enabled", havingValue = "true")
public class GoogleDriveStorageAdapter implements DocumentStoragePort {

    private static final String NOT_WIRED =
            "Google Drive storage is enabled but its client libraries are not bundled in this build. "
            + "Add the Drive dependencies (docs/INTEGRATION.md §2) and complete GoogleDriveStorageAdapter, "
            + "or set eduflow.google-drive.enabled=false to use local filesystem storage.";

    public GoogleDriveStorageAdapter(GoogleDriveProperties properties) {
        log.warn("Google Drive storage selected but the adapter is a seam only — {}", NOT_WIRED);
    }

    @Override
    public StoredFile store(StorageContext context, MultipartFile file) {
        throw new DocumentStorageException(NOT_WIRED);
    }

    @Override
    public StoredFile replace(String storageKey, StorageContext context, MultipartFile file) {
        throw new DocumentStorageException(NOT_WIRED);
    }

    @Override
    public Resource load(String storageKey) {
        throw new DocumentStorageException(NOT_WIRED);
    }

    @Override
    public void delete(String storageKey) {
        throw new DocumentStorageException(NOT_WIRED);
    }
}
