package com.eduflow.user;

/**
 * Lifecycle states for a platform user account.
 */
public enum UserStatus {
    /** Account is active and can authenticate. */
    ACTIVE,
    /** Account has been deactivated by an admin. */
    INACTIVE,
    /** Account is temporarily locked (e.g. too many failed login attempts). */
    LOCKED,
    /** Account is awaiting email verification. */
    PENDING_VERIFICATION
}

