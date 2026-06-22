package com.eduflow.application;

import com.eduflow.common.BaseEntity;
import com.eduflow.student.Student;
import com.eduflow.tenant.Tenant;
import com.eduflow.university.Course;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A student's application to a university {@link Course}.
 *
 * <p>Status transitions are enforced in {@code ApplicationService}. A student may
 * not have two applications to the same course (uq_applications_student_course).</p>
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application extends BaseEntity {

    /** The consultancy this application belongs to. Never null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    /** Set when the application transitions to {@code SUBMITTED}. */
    @Column(name = "applied_date")
    private Instant appliedDate;

    /** Set on offer or rejection. */
    @Column(name = "decision_date")
    private Instant decisionDate;

    @Column(name = "notes", length = 1000)
    private String notes;
}
