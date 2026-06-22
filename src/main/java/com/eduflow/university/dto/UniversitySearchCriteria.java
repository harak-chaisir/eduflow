package com.eduflow.university.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query parameters for dynamic university search. All fields optional; null values
 * are ignored by {@link com.eduflow.university.UniversitySpecification}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UniversitySearchCriteria {

    /** Partial match against name or code (case-insensitive). */
    private String q;

    /** Filter by exact country. */
    private String country;

    /** Filter by active flag; null returns both. */
    private Boolean active;
}
