package com.eduflow.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound payload to create or update a workflow template's properties (PRD §6).
 *
 * <p>Mutable {@code @Data} bean — Boot 4 / Jackson 3 cannot deserialize {@code @Value}
 * request DTOs.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTemplateRequest {

    @NotBlank(message = "Workflow name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;

    /** Defaults to active when omitted on create. */
    private Boolean active;
}
