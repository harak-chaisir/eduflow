package com.eduflow.document.dto;

import com.eduflow.document.DocumentStatus;

/**
 * A documentation officer's verdict on a document, mapped to the resulting
 * {@link DocumentStatus}. {@code REJECT} and {@code REQUEST_REVISION} require remarks
 * (enforced in the service so both the REST API and the staff UI obey the rule).
 */
public enum VerificationDecision {

    APPROVE(DocumentStatus.APPROVED, false),
    REJECT(DocumentStatus.REJECTED, true),
    REQUEST_REVISION(DocumentStatus.NEEDS_REVISION, true);

    private final DocumentStatus targetStatus;
    private final boolean remarksRequired;

    VerificationDecision(DocumentStatus targetStatus, boolean remarksRequired) {
        this.targetStatus = targetStatus;
        this.remarksRequired = remarksRequired;
    }

    public DocumentStatus targetStatus() {
        return targetStatus;
    }

    public boolean isRemarksRequired() {
        return remarksRequired;
    }
}
