package com.eduflow.document;

import java.util.Set;

/**
 * Lifecycle status of a student document submission.
 *
 * <pre>
 *   PENDING        → APPROVED | REJECTED | NEEDS_REVISION
 *   NEEDS_REVISION → PENDING (on resubmit) | REJECTED
 *   APPROVED       → (terminal)
 *   REJECTED       → (terminal)
 * </pre>
 *
 * <p>The transition rules live on the enum itself via {@link #canTransitionTo}; the
 * service validates every change against them and throws
 * {@link InvalidDocumentStatusTransitionException} on a violation.</p>
 */
public enum DocumentStatus {

    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    NEEDS_REVISION("Needs revision");

    private final String label;

    DocumentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Returns {@code true} if a document may move from this status to {@code target}. */
    public boolean canTransitionTo(DocumentStatus target) {
        return switch (this) {
            case PENDING -> Set.of(APPROVED, REJECTED, NEEDS_REVISION).contains(target);
            case NEEDS_REVISION -> Set.of(PENDING, REJECTED).contains(target);
            case APPROVED, REJECTED -> false;
        };
    }
}
