package com.eduflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Drive storage configuration (see docs/INTEGRATION.md §3).
 *
 * <p>Disabled by default — the local filesystem adapter is used unless
 * {@code eduflow.google-drive.enabled=true}.</p>
 */
@ConfigurationProperties(prefix = "eduflow.google-drive")
public class GoogleDriveProperties {

    /** Master switch; when true the Google Drive storage adapter is selected. */
    private boolean enabled = false;

    /** Application name reported to the Drive API. */
    private String applicationName = "EduFlow CRM";

    /** Spring resource location of the service-account JSON key. */
    private String credentialsLocation;

    /** Folder id under which per-tenant trees are created (ideally on a Shared Drive). */
    private String rootFolderId;

    /** Whether the root folder lives on a Shared Drive (sets supportsAllDrives=true). */
    private boolean sharedDrive = true;

    /** Optional domain-wide delegation user to impersonate. */
    private String impersonatedUser;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }

    public String getCredentialsLocation() { return credentialsLocation; }
    public void setCredentialsLocation(String credentialsLocation) { this.credentialsLocation = credentialsLocation; }

    public String getRootFolderId() { return rootFolderId; }
    public void setRootFolderId(String rootFolderId) { this.rootFolderId = rootFolderId; }

    public boolean isSharedDrive() { return sharedDrive; }
    public void setSharedDrive(boolean sharedDrive) { this.sharedDrive = sharedDrive; }

    public String getImpersonatedUser() { return impersonatedUser; }
    public void setImpersonatedUser(String impersonatedUser) { this.impersonatedUser = impersonatedUser; }
}
