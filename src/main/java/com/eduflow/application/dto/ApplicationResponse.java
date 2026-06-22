package com.eduflow.application.dto;

import com.eduflow.application.Application;
import com.eduflow.application.ApplicationStatus;
import com.eduflow.university.CourseLevel;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable response representation of an {@link Application}, denormalizing the
 * student, university and course names so the UI needs no extra fetches.
 */
@Value
@Builder
public class ApplicationResponse {

    UUID id;
    ApplicationStatus status;
    Instant appliedDate;
    Instant decisionDate;
    String notes;

    UUID studentId;
    String studentName;

    UUID courseId;
    String courseName;
    CourseLevel courseLevel;

    UUID universityId;
    String universityName;

    UUID tenantId;

    Instant createdAt;
    Instant updatedAt;
    String createdBy;

    public static ApplicationResponse from(Application a) {
        var course = a.getCourse();
        var university = course.getUniversity();
        return ApplicationResponse.builder()
                .id(a.getId())
                .status(a.getStatus())
                .appliedDate(a.getAppliedDate())
                .decisionDate(a.getDecisionDate())
                .notes(a.getNotes())
                .studentId(a.getStudent().getId())
                .studentName(a.getStudent().getFullName())
                .courseId(course.getId())
                .courseName(course.getName())
                .courseLevel(course.getLevel())
                .universityId(university.getId())
                .universityName(university.getName())
                .tenantId(a.getTenant().getId())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .createdBy(a.getCreatedBy())
                .build();
    }
}
