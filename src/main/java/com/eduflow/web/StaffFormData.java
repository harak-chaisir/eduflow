package com.eduflow.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable form-binding object for the staff invite / edit Thymeleaf forms.
 *
 * <p>Web-layer concern only; mapped to
 * {@link com.eduflow.user.dto.InviteStaffRequest} or
 * {@link com.eduflow.user.dto.UpdateStaffRequest} before being passed to the service.
 * The {@code email} field is only used on invite (it is read-only when editing).</p>
 */
@Data
@NoArgsConstructor
public class StaffFormData {

    @NotBlank(message = "Email address is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    /** Selected authority names bound from the role checkboxes, e.g. {@code ROLE_COUNSELOR}. */
    @NotEmpty(message = "Select at least one role")
    private List<String> roleNames = new ArrayList<>();
}
