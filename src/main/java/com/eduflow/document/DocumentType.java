package com.eduflow.document;

import java.util.Arrays;
import java.util.List;

/**
 * Catalogue of documents that can be collected for a student.
 *
 * <p>Each type carries its {@link DocumentCategory}, a display label, and a
 * {@code coreRequirement} flag. The set of core-required types drives the
 * dossier-readiness meter (see {@code DocumentService#getDossier}).</p>
 *
 * <p>Stored as a {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)}; the enum
 * names are pinned by the {@code chk_documents_type} constraint, so do not rename.</p>
 */
public enum DocumentType {

    // ── Academic ──────────────────────────────────────────────────────────────
    SEE_CERTIFICATE("SEE Certificate", DocumentCategory.ACADEMIC, false),
    PLUS_TWO_TRANSCRIPT("+2 / High School Transcript", DocumentCategory.ACADEMIC, true),
    BACHELOR_TRANSCRIPT("Bachelor Transcript", DocumentCategory.ACADEMIC, false),
    DEGREE_CERTIFICATE("Degree Certificate", DocumentCategory.ACADEMIC, false),
    RECOMMENDATION_LETTER("Recommendation Letter", DocumentCategory.ACADEMIC, false),

    // ── Financial ─────────────────────────────────────────────────────────────
    BANK_STATEMENT("Bank Statement", DocumentCategory.FINANCIAL, true),
    SPONSORSHIP_LETTER("Sponsorship Letter", DocumentCategory.FINANCIAL, false),
    TAX_CLEARANCE("Tax Clearance", DocumentCategory.FINANCIAL, false),

    // ── Identity ──────────────────────────────────────────────────────────────
    PASSPORT("Passport", DocumentCategory.IDENTITY, true),
    CITIZENSHIP("Citizenship", DocumentCategory.IDENTITY, false),
    PHOTOGRAPH("Photograph", DocumentCategory.IDENTITY, true),

    // ── English Proficiency ───────────────────────────────────────────────────
    IELTS("IELTS", DocumentCategory.ENGLISH_PROFICIENCY, false),
    PTE("PTE", DocumentCategory.ENGLISH_PROFICIENCY, false),
    TOEFL("TOEFL", DocumentCategory.ENGLISH_PROFICIENCY, false),

    // ── Application-related ───────────────────────────────────────────────────
    OFFER_LETTER("Offer Letter", DocumentCategory.OFFER_LETTERS, false),
    VISA_DOCS("Visa Documents", DocumentCategory.VISA, false);

    private final String label;
    private final DocumentCategory category;
    private final boolean coreRequirement;

    DocumentType(String label, DocumentCategory category, boolean coreRequirement) {
        this.label = label;
        this.category = category;
        this.coreRequirement = coreRequirement;
    }

    /** Human-readable label for the UI. */
    public String label() {
        return label;
    }

    /** The category (and storage folder) this type belongs to. */
    public DocumentCategory category() {
        return category;
    }

    /** Whether this type is part of the default required set used for readiness. */
    public boolean isCoreRequirement() {
        return coreRequirement;
    }

    /** All core-required types, in declaration order. */
    public static List<DocumentType> coreRequirements() {
        return Arrays.stream(values()).filter(DocumentType::isCoreRequirement).toList();
    }
}
