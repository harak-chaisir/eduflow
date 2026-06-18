package com.eduflow.document.storage;

/**
 * Raised when the document storage backend fails (I/O error, Drive API error, missing
 * content). Mapped to a 502/500 by the global exception handler so storage outages never
 * leak as opaque 500s with stack details.
 */
public class DocumentStorageException extends RuntimeException {

    public DocumentStorageException(String message) {
        super(message);
    }

    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
