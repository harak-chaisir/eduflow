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
 * Request body for updating an existing student's profile.
 *
 * <p>All fields are optional; only non-null fields are applied by the service.
 * Constraints mirror {@link RegisterStudentRequest} where applicable.</p>
 *
 * <p>Modelled as a mutable bean (no-arg constructor + setters) so Jackson can
 * deserialize it directly; {@code @Builder} is retained for programmatic use.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStudentRequest {

    @Size(max = 100, message = "First name must not exceed 100 characters")
    String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    String lastName;

    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email;

    @Pattern(
            regexp = "^[+]?[0-9\\s\\-().]{7,30}$",
            message = "Phone number format is invalid"
    )
    String phone;

    /** Date of birth; must be in the past if provided. */
    @Past(message = "Date of birth must be in the past")
    Instant dateOfBirth;

    Gender gender;

    @Size(max = 100, message = "Nationality must not exceed 100 characters")
    String nationality;

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

    List<@Size(max = 100) String> interestedCountries;

    List<@Size(max = 255) String> interestedCourses;

    /** Pass {@code null} to leave the assignment unchanged; pass the counselor's UUID to (re-)assign. */
    UUID assignedCounselorId;
}

