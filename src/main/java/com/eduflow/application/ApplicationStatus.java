package com.eduflow.application;

/**
 * Lifecycle status of a student {@link Application}.
 *
 * <p>Allowed transitions (enforced in {@code ApplicationService}):
 * <pre>
 *   DRAFT → SUBMITTED → UNDER_REVIEW → CONDITIONAL_OFFER | UNCONDITIONAL_OFFER | REJECTED
 *   SUBMITTED → REJECTED
 *   CONDITIONAL_OFFER → UNCONDITIONAL_OFFER | REJECTED
 * </pre>
 * {@code UNCONDITIONAL_OFFER} and {@code REJECTED} are terminal.</p>
 */
public enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    CONDITIONAL_OFFER,
    UNCONDITIONAL_OFFER,
    REJECTED
}
