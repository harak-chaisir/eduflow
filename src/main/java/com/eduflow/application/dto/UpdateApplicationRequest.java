package com.eduflow.application.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating an {@link com.eduflow.application.Application}'s notes.
 * Status changes go through the dedicated status endpoint, not here.
 *
 * <p>Mutable {@code @Data} bean so Jackson 3 / Boot 4 can deserialize it directly.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApplicationRequest {

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
}
