package com.eduflow.tenant.dto;

import com.eduflow.tenant.TenantPlan;
import com.eduflow.tenant.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query parameters for the super-admin tenant list. All fields are optional;
 * null values are ignored by {@link com.eduflow.tenant.TenantSpecification}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSearchCriteria {

    /** Partial match against name or slug (case-insensitive). */
    String q;

    /** Filter by exact lifecycle status. */
    TenantStatus status;

    /** Filter by exact plan. */
    TenantPlan plan;
}
