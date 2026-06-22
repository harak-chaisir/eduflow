package com.eduflow.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mutable form-binding object for the course create / edit Thymeleaf forms.
 *
 * <p>Numeric inputs are bound as strings and parsed in the controller so that an
 * empty input maps cleanly to {@code null} (matching the StudentFormData pattern).</p>
 */
@Data
@NoArgsConstructor
public class CourseFormData {

    @NotBlank(message = "Course name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    /** Matches {@link com.eduflow.university.CourseLevel} enum name, e.g. {@code "MASTER"}. */
    @NotBlank(message = "Course level is required")
    private String level;

    /** Intake month 1–12 as a string; parsed in the controller. */
    private String intakeMonth;

    private String intakeYear;

    private String tuitionFee;

    @Size(max = 1000)
    private String entryRequirements;

    private boolean active = true;
}
