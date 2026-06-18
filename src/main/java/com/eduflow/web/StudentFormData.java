package com.eduflow.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Mutable form-binding object for the student create / edit Thymeleaf forms.
 *
 * <p>This class is a web-layer concern only. It is mapped to
 * {@link com.eduflow.student.dto.RegisterStudentRequest} or
 * {@link com.eduflow.student.dto.UpdateStudentRequest} before being passed to the service.</p>
 */
@Data
@NoArgsConstructor
public class StudentFormData {

    // ── Required fields ──────────────────────────────────────────────────────

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Email address is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    // ── Optional personal ────────────────────────────────────────────────────

    @Pattern(regexp = "^$|^[+]?[0-9\\s\\-().]{7,30}$",
            message = "Enter a valid phone number (7–30 digits; +, -, (), spaces allowed)")
    private String phone;

    /** Date string in {@code yyyy-MM-dd} format, bound from an HTML {@code <input type="date">}. */
    private String dateOfBirth;

    /** Matches {@link com.eduflow.student.Gender} enum name, e.g. {@code "MALE"}. */
    private String gender;

    @Size(max = 100, message = "Nationality must not exceed 100 characters")
    private String nationality;

    // ── Address ──────────────────────────────────────────────────────────────

    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String stateProvince;

    @Size(max = 100)
    private String country;

    @Size(max = 20)
    private String postalCode;

    // ── Interests ────────────────────────────────────────────────────────────

    /**
     * Comma-separated countries the student is interested in studying in.
     * Example: {@code "UK, Australia, Canada"}
     */
    private String interestedCountries;

    /**
     * Comma-separated courses / fields of study.
     * Example: {@code "Computer Science, Business Administration"}
     */
    private String interestedCourses;

    // ── Assignment ───────────────────────────────────────────────────────────

    /** UUID of the counselor to assign, selected from the tenant's counselors. Optional. */
    private UUID assignedCounselorId;
}

