package com.eduflow.university.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating or updating a {@link com.eduflow.university.University}.
 *
 * <p>Mutable {@code @Data} bean so Jackson 3 / Boot 4 can deserialize it directly.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UniversityRequest {

    @NotBlank(message = "University name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 255, message = "Website must not exceed 255 characters")
    private String website;

    @Size(max = 50, message = "Code must not exceed 50 characters")
    private String code;

    /** Whether the university is active. Null on create defaults to {@code true}. */
    private Boolean active;
}
