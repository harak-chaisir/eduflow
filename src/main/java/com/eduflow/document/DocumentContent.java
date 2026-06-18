package com.eduflow.document;

import org.springframework.core.io.Resource;

/**
 * A document's downloadable content plus the metadata needed to set response headers.
 */
public record DocumentContent(Resource resource, String filename, String mimeType, Long sizeBytes) {}
