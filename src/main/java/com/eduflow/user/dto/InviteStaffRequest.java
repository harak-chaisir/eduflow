package com.eduflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * Inbound payload for inviting a new staff member.
 *
 * <p>Mutable {@code @Data} bean (not {@code @Value}) so Jackson 3 / Spring Boot 4
 * can deserialise it — see the Jackson-3 immutable-DTO gotcha.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteStaffRequest {

    @NotBlank(message = "Email address is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    /** Spring Security authority names to grant, e.g. {@code ROLE_COUNSELOR}. */
    @NotEmpty(message = "Select at least one role")
    @Builder.Default
    private Set<String> roleNames = new HashSet<>();
}
