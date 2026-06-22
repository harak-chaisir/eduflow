package com.eduflow.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for creating an {@link com.eduflow.application.Application}. The student
 * is taken from the path; the course and notes come from the body.
 *
 * <p>Mutable {@code @Data} bean so Jackson 3 / Boot 4 can deserialize it directly.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApplicationRequest {

    @NotNull(message = "Course is required")
    private UUID courseId;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
}
