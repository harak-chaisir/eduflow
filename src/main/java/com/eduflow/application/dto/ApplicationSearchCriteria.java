package com.eduflow.application.dto;

import com.eduflow.application.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Query parameters for dynamic application search. All fields optional; null values
 * are ignored by {@link com.eduflow.application.ApplicationSpecification}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSearchCriteria {

    private UUID studentId;
    private UUID courseId;
    private UUID universityId;
    private ApplicationStatus status;
}
