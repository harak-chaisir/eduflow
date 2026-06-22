package com.eduflow.user.dto;

import com.eduflow.user.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query parameters for dynamic staff search.
 *
 * <p>All fields are optional; null values are ignored by
 * {@link com.eduflow.user.StaffSpecification}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffSearchCriteria {

    /** Partial match against first name or last name (case-insensitive). */
    private String name;

    /** Partial match against the email address (case-insensitive). */
    private String email;

    /** Filter by exact account status. */
    private UserStatus status;

    /** Filter to users holding this authority name, e.g. {@code ROLE_COUNSELOR}. */
    private String role;
}
