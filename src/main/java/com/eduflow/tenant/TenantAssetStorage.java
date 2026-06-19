package com.eduflow.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * Local-filesystem storage for tenant branding assets (currently logos). Files are written
 * under a configured root ({@code eduflow.tenant.logo-root}) as
 * {@code {root}/{tenantId}/{uuid}__{filename}}; the returned relative key is stored on
 * {@link TenantSettings#getLogoReference()} and used later to serve the image.
 *
 * <p>This is intentionally separate from the student-document storage port, whose context is
 * student/category specific.</p>
 */
@Slf4j
@Component
public class TenantAssetStorage {

    private final Path root;

    public TenantAssetStorage(
            @Value("${eduflow.tenant.logo-root:./var/eduflow-tenant-logos}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new TenantAssetStorageException("Could not initialise tenant logo storage root: " + root, e);
        }
        log.info("Tenant asset storage: local filesystem root={}", root);
    }

    /** Stores an uploaded logo image and returns its relative storage key. */
    public String storeLogo(UUID tenantId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new TenantAssetStorageException("Logo file is empty.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new TenantAssetStorageException("Logo must be an image file.");
        }
        String safeName = StringUtils.cleanPath(
                        file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                                ? "logo" : file.getOriginalFilename())
                .replace("/", "_").replace("\\", "_");
        String key = tenantId + "/" + UUID.randomUUID() + "__" + safeName;

        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new TenantAssetStorageException("Failed to store tenant logo.", e);
        }
        return key;
    }

    /** Opens a stored logo for serving. */
    public Resource loadLogo(String storageKey) {
        Path target = resolve(storageKey);
        if (!Files.isReadable(target)) {
            throw new TenantAssetStorageException("Logo not found: " + storageKey);
        }
        return new PathResource(target);
    }

    /** Best-effort removal; never throws if already absent. */
    public void delete(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("Could not delete tenant logo {}: {}", storageKey, e.getMessage());
        }
    }

    /** Resolves a storage key against the root, rejecting path traversal. */
    private Path resolve(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new TenantAssetStorageException("Illegal storage key: " + storageKey);
        }
        return target;
    }
}
