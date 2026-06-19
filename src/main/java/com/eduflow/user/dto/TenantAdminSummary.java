package com.eduflow.user.dto;

import com.eduflow.user.User;
import com.eduflow.user.UserStatus;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Immutable summary of a tenant admin, used to populate the super-admin
 * "reset admin password" picker.
 *
 * <p>Intentionally carries no credential fields — {@code passwordHash} must never
 * leave the persistence layer.</p>
 */
@Value
@Builder
public class TenantAdminSummary {

    UUID id;
    String email;
    String fullName;
    UserStatus status;

    public static TenantAdminSummary from(User u) {
        return TenantAdminSummary.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName().trim())
                .status(u.getStatus())
                .build();
    }
}
