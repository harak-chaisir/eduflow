package com.eduflow.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional filters for listing/searching workflow templates within a tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSearchCriteria {

    /** Partial, case-insensitive match on the template name. */
    private String name;

    /** Exact match on country. */
    private String country;

    /** Filter by active flag; null = no filter. */
    private Boolean active;

    /** Filter by archived flag; null = no filter. */
    private Boolean archived;
}
