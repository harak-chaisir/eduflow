package com.eduflow.user.dto;

/**
 * Result of inviting a staff member: the created staff record plus the single-use
 * set-password token issued for them. The web layer builds the activation link from
 * the token (so it matches the host the operator is actually using).
 *
 * @param staff the newly invited staff member (status {@code PENDING_VERIFICATION})
 * @param token the opaque set-password token to embed in the invite link
 */
public record StaffInviteResult(StaffResponse staff, String token) {
}
