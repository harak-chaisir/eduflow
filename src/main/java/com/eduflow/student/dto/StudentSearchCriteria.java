package com.eduflow.student.dto;

import com.eduflow.student.StudentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Query parameters for dynamic student search.
 *
 * <p>All fields are optional; null values are ignored by {@link com.eduflow.student.StudentSpecification}.
 * Pagination and sort are handled via Spring's {@link org.springframework.data.domain.Pageable}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSearchCriteria {

    /**
     * Partial match against first name or last name (case-insensitive).
     */
    String name;

    /**
     * Partial match against the student's email address (case-insensitive).
     */
    String email;

    /**
     * Filter by exact lifecycle status.
     */
    StudentStatus status;

    /**
     * Filter by assigned counselor UUID.
     */
    UUID assignedCounselorId;
}

