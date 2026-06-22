package com.eduflow.university.dto;

import com.eduflow.university.Course;
import com.eduflow.university.CourseLevel;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable response representation of a {@link Course}, denormalizing the owning
 * university's name so callers don't need a second fetch.
 */
@Value
@Builder
public class CourseResponse {

    UUID id;
    String name;
    CourseLevel level;
    Integer intakeMonth;
    Integer intakeYear;
    BigDecimal tuitionFee;
    String entryRequirements;
    boolean active;

    UUID universityId;
    String universityName;
    String universityCountry;

    UUID tenantId;

    Instant createdAt;
    Instant updatedAt;
    String createdBy;

    public static CourseResponse from(Course c) {
        return CourseResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .level(c.getLevel())
                .intakeMonth(c.getIntakeMonth())
                .intakeYear(c.getIntakeYear())
                .tuitionFee(c.getTuitionFee())
                .entryRequirements(c.getEntryRequirements())
                .active(c.isActive())
                .universityId(c.getUniversity().getId())
                .universityName(c.getUniversity().getName())
                .universityCountry(c.getUniversity().getCountry())
                .tenantId(c.getTenant().getId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .createdBy(c.getCreatedBy())
                .build();
    }
}
