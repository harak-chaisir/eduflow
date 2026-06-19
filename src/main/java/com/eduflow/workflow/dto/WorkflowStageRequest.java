package com.eduflow.workflow.dto;

import com.eduflow.document.DocumentType;
import com.eduflow.workflow.StageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Inbound payload to create or update a {@link com.eduflow.workflow.WorkflowStage}
 * (PRD §7, §10, §11, §12).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStageRequest {

    @NotBlank(message = "Stage name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    @NotBlank(message = "Stage code is required")
    @Size(max = 60, message = "Code must not exceed 60 characters")
    private String code;

    /** Position; if null on create the stage is appended to the end. */
    private Integer displayOrder;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Size(max = 20, message = "Color must not exceed 20 characters")
    private String color;

    private Boolean active;

    @Positive(message = "SLA days must be a positive number")
    private Integer slaDays;

    private StageType stageType;

    /** Role responsible for this stage, e.g. {@code ROLE_DOC_OFFICER}. Optional. */
    @Size(max = 60, message = "Owner role must not exceed 60 characters")
    private String ownerRole;

    /** Documents required before progressing past this stage. */
    private List<DocumentType> requiredDocuments;
}
