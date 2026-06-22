package com.eduflow.university.dto;

import com.eduflow.university.CourseLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for creating or updating a {@link com.eduflow.university.Course}.
 *
 * <p>Mutable {@code @Data} bean so Jackson 3 / Boot 4 can deserialize it directly.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRequest {

    @NotBlank(message = "Course name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotNull(message = "Course level is required")
    private CourseLevel level;

    @Min(value = 1, message = "Intake month must be between 1 and 12")
    @Max(value = 12, message = "Intake month must be between 1 and 12")
    private Integer intakeMonth;

    private Integer intakeYear;

    private BigDecimal tuitionFee;

    @Size(max = 1000, message = "Entry requirements must not exceed 1000 characters")
    private String entryRequirements;

    /** Whether the course is active. Null on create defaults to {@code true}. */
    private Boolean active;
}
