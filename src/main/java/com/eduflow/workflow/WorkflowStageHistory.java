package com.eduflow.workflow;

import com.eduflow.common.BaseEntity;
import com.eduflow.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per stage occupancy for a {@link StudentWorkflow} — opened when a student
 * enters a stage and closed ({@code exitedAt}) when they leave it. Drives per-stage
 * duration metrics, SLA tracking, and analytics.
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "workflow_stage_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStageHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_workflow_id", nullable = false)
    private StudentWorkflow studentWorkflow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private WorkflowStage stage;

    @Column(name = "entered_at", nullable = false)
    @Builder.Default
    private Instant enteredAt = Instant.now();

    @Column(name = "exited_at")
    private Instant exitedAt;

    @Column(name = "moved_by_user_id")
    private UUID movedByUserId;

    @Column(name = "transition_id")
    private UUID transitionId;

    @Column(name = "notes", length = 1000)
    private String notes;
}
