package com.eduflow.document;

/**
 * High-level grouping a {@link DocumentType} belongs to. Each category maps to a
 * human-readable folder name used when laying out a student's document tree in the
 * configured storage backend (local filesystem or Google Drive).
 */
public enum DocumentCategory {

    ACADEMIC("Academic"),
    FINANCIAL("Financial"),
    IDENTITY("Identity"),
    ENGLISH_PROFICIENCY("English Proficiency"),
    OFFER_LETTERS("Offer Letters"),
    VISA("Visa");

    private final String folderName;

    DocumentCategory(String folderName) {
        this.folderName = folderName;
    }

    /** The exact folder name used for this category in the storage tree. */
    public String folderName() {
        return folderName;
    }
}
