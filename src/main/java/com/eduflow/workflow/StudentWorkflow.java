package com.eduflow.workflow;

import com.eduflow.common.BaseEntity;
import com.eduflow.student.Student;
import com.eduflow.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A {@link WorkflowTemplate} assigned to a {@link Student} — the running instance
 * (PRD §4 "Workflow Instance", §14 "Student Workflow Execution").
 *
 * <p>{@code currentStage} is the source of truth for the student's process state; the
 * coarse {@code Student.status} field is intentionally left independent. {@code
 * currentStageEnteredAt} drives the on-read SLA calculation.</p>
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "student_workflows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentWorkflow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_template_id", nullable = false)
    private WorkflowTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_stage_id")
    private WorkflowStage currentStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private InstanceStatus status = InstanceStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    /** When the student entered {@link #currentStage}; basis for SLA day counting. */
    @Column(name = "current_stage_entered_at", nullable = false)
    @Builder.Default
    private Instant currentStageEnteredAt = Instant.now();

    /** Maintained by the SLA job (Phase 4); complements on-read computation. */
    @Column(name = "sla_breached", nullable = false)
    @Builder.Default
    private boolean slaBreached = false;
}
