package com.eduflow.document.dto;

import com.eduflow.document.DocumentCategory;
import com.eduflow.document.DocumentStatus;
import com.eduflow.document.DocumentType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * View model for a student's document dossier: the readiness meter, per-status tallies,
 * and documents grouped by category (including placeholder rows for required types that
 * have not been uploaded yet).
 */
public record DossierView(
        UUID studentId,
        String studentName,
        Readiness readiness,
        Map<DocumentStatus, Long> statusTally,
        List<CategoryGroup> categories
) {

    /**
     * Dossier readiness over the core-required document set.
     *
     * @param requiredApproved number of required types with an APPROVED upload
     * @param requiredTotal    total number of required types
     * @param percent          requiredApproved / requiredTotal as a 0–100 integer
     */
    public record Readiness(int requiredApproved, int requiredTotal, int percent) {
        public boolean complete() {
            return requiredTotal > 0 && requiredApproved == requiredTotal;
        }
    }

    /** A category section with its document rows. */
    public record CategoryGroup(DocumentCategory category, String label, List<Row> rows) {}

    /**
     * A single dossier row. For an uploaded document {@code document} is present and
     * {@code uploaded} is true; for a required-but-missing type it is a placeholder
     * ({@code uploaded=false}, {@code document=null}).
     */
    public record Row(DocumentType type, String label, boolean required, boolean uploaded,
                      DocumentResponse document) {}
}
