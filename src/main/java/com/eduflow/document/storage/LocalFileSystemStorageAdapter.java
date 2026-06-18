package com.eduflow.document.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Default {@link DocumentStoragePort} that persists files on the local filesystem under a
 * configured root. Active unless the Google Drive adapter is enabled
 * ({@code eduflow.google-drive.enabled=true}).
 *
 * <p>Layout: {@code {root}/{tenantId}/{studentId}/{CATEGORY}/{uuid}__{filename}}. The
 * returned {@code storageKey} is the path relative to the root, which is what the
 * document record stores.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "eduflow.google-drive.enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileSystemStorageAdapter implements DocumentStoragePort {

    private final Path root;

    public LocalFileSystemStorageAdapter(DocumentStorageProperties properties) {
        this.root = Paths.get(properties.getLocalRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new DocumentStorageException("Could not initialise local storage root: " + root, e);
        }
        log.info("Document storage: local filesystem adapter active (root={})", root);
    }

    @Override
    public StoredFile store(StorageContext context, MultipartFile file) {
        String relativeKey = buildKey(context, file.getOriginalFilename());
        return writeTo(relativeKey, file);
    }

    @Override
    public StoredFile replace(String storageKey, StorageContext context, MultipartFile file) {
        // Keep the same key so existing references remain valid; just overwrite the bytes.
        return writeTo(storageKey, file);
    }

    @Override
    public Resource load(String storageKey) {
        Path target = resolve(storageKey);
        if (!Files.isReadable(target)) {
            throw new DocumentStorageException("Stored file not found: " + storageKey);
        }
        return new PathResource(target);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("Could not delete stored file {}: {}", storageKey, e.getMessage());
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private StoredFile writeTo(String relativeKey, MultipartFile file) {
        Path target = resolve(relativeKey);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to store document file", e);
        }
        return new StoredFile(relativeKey, file.getSize(), file.getContentType(), null);
    }

    private String buildKey(StorageContext ctx, String originalFilename) {
        String safeName = StringUtils.cleanPath(
                originalFilename == null || originalFilename.isBlank() ? "file" : originalFilename)
                .replace("/", "_").replace("\\", "_");
        return ctx.tenantId() + "/" + ctx.studentId() + "/" + ctx.category().name()
                + "/" + UUID.randomUUID() + "__" + safeName;
    }

    /** Resolves a storage key against the root, rejecting path traversal. */
    private Path resolve(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new DocumentStorageException("Illegal storage key: " + storageKey);
        }
        return target;
    }
}
