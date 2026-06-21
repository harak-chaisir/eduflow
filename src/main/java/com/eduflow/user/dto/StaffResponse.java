package com.eduflow.user.dto;

import com.eduflow.role.Role;
import com.eduflow.user.User;
import com.eduflow.user.UserStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable response representation of a staff {@link User}.
 *
 * <p>⚠️ Never exposes {@code passwordHash} or any credential/token.</p>
 */
@Value
@Builder
public class StaffResponse {

    UUID id;
    String email;
    String firstName;
    String lastName;
    String fullName;
    UserStatus status;
    boolean emailVerified;

    /** Granted authority names, e.g. {@code ROLE_COUNSELOR}, sorted for stable display. */
    List<String> roleNames;

    UUID tenantId;

    Instant createdAt;
    Instant updatedAt;
    String createdBy;

    /**
     * Maps a {@link User} entity to a {@link StaffResponse}. Requires {@code roles}
     * to be initialised (the service loads them within the transaction).
     */
    public static StaffResponse from(User user) {
        return StaffResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName().trim())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .roleNames(user.getRoles().stream()
                        .map(Role::getName)
                        .sorted()
                        .toList())
                .tenantId(user.getTenant() != null ? user.getTenant().getId() : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .createdBy(user.getCreatedBy())
                .build();
    }
}
