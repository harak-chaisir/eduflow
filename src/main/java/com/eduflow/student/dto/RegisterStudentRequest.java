package com.eduflow.student.dto;

import com.eduflow.student.Gender;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request body for registering a new student.
 *
 * <p>All personally identifiable and contact fields are validated here so that
 * the service layer can assume clean data on entry.</p>
 *
 * <p>Modelled as a mutable bean (no-arg constructor + setters) so Jackson can
 * deserialize it directly; {@code @Builder} is retained for programmatic use.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterStudentRequest {

    // ── Required fields ──────────────────────────────────────────────────────

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email;

    // ── Optional fields ──────────────────────────────────────────────────────

    @Pattern(
            regexp = "^[+]?[0-9\\s\\-().]{7,30}$",
            message = "Phone number format is invalid"
    )
    String phone;

    /** Date of birth; must be in the past. */
    @Past(message = "Date of birth must be in the past")
    Instant dateOfBirth;

    Gender gender;

    @Size(max = 100, message = "Nationality must not exceed 100 characters")
    String nationality;

    // ── Address ──────────────────────────────────────────────────────────────

    @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
    String addressLine1;

    @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
    String addressLine2;

    @Size(max = 100, message = "City must not exceed 100 characters")
    String city;

    @Size(max = 100, message = "State / province must not exceed 100 characters")
    String stateProvince;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    String country;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    String postalCode;

    // ── Interests ────────────────────────────────────────────────────────────

    List<@Size(max = 100) String> interestedCountries;

    List<@Size(max = 255) String> interestedCourses;

    // ── Assignment ───────────────────────────────────────────────────────────

    /** Optional UUID of the counselor to assign this student to. */
    UUID assignedCounselorId;
}

