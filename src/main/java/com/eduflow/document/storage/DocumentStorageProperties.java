package com.eduflow.document.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for document storage.
 *
 * <pre>
 * eduflow.documents.local-root = ./var/eduflow-documents   # local filesystem adapter root
 * </pre>
 */
@ConfigurationProperties(prefix = "eduflow.documents")
public class DocumentStorageProperties {

    /** Root directory for the local filesystem storage adapter. */
    private String localRoot = "./var/eduflow-documents";

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }
}
