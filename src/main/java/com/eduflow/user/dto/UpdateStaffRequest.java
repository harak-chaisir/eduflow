package com.eduflow.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * Inbound payload for editing a staff member's profile and role assignment.
 *
 * <p>Email is intentionally not editable here (it is the tenant-unique login key).
 * Mutable {@code @Data} bean for Jackson 3 / Spring Boot 4 compatibility.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStaffRequest {

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    /** Full replacement set of authority names, e.g. {@code ROLE_COUNSELOR}. */
    @NotEmpty(message = "Select at least one role")
    @Builder.Default
    private Set<String> roleNames = new HashSet<>();
}
