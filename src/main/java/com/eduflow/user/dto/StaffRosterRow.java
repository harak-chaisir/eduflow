package com.eduflow.user.dto;

import com.eduflow.user.UserStatus;

import java.util.List;
import java.util.UUID;

/**
 * A single row in the staff roster table: the staff member plus their caseload context.
 *
 * @param id        user id
 * @param fullName  display name (falls back to email when blank)
 * @param firstName for the avatar initial
 * @param email     login email
 * @param roleNames granted authority names, sorted
 * @param status    account status
 * @param counselor whether this user holds {@code ROLE_COUNSELOR} (drives the caseload cell)
 * @param caseload  active caseload count for counselors; {@code null} for non-counselors
 * @param roleNote  short caption for non-counselors (e.g. "Reviews all documents")
 */
public record StaffRosterRow(
        UUID id,
        String fullName,
        String firstName,
        String email,
        List<String> roleNames,
        UserStatus status,
        boolean counselor,
        Long caseload,
        String roleNote) {
}
