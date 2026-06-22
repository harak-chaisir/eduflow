package com.eduflow.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mutable form-binding object for the university create / edit Thymeleaf forms.
 *
 * <p>Web-layer concern only; mapped to
 * {@link com.eduflow.university.dto.UniversityRequest} before reaching the service.</p>
 */
@Data
@NoArgsConstructor
public class UniversityFormData {

    @NotBlank(message = "University name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;

    @Size(max = 100)
    private String city;

    @Size(max = 255)
    private String website;

    @Size(max = 50)
    private String code;

    /** Checkbox; defaults true for new records. */
    private boolean active = true;
}
