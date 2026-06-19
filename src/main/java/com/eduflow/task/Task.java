package com.eduflow.task;

import com.eduflow.common.BaseEntity;
import com.eduflow.student.Student;
import com.eduflow.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A unit of work generated from a workflow stage and owned by a role (PRD §4 "Task", §7.7).
 *
 * <p>Tenant-scoped. References to the originating workflow instance and stage are kept by
 * id (loose coupling to the workflow module). Status follows
 * {@code PENDING → IN_PROGRESS → COMPLETED | CANCELLED}.</p>
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** Originating workflow instance id (loose link to the workflow module). */
    @Column(name = "student_workflow_id")
    private UUID studentWorkflowId;

    /** Originating stage id. */
    @Column(name = "stage_id")
    private UUID stageId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    /** Role responsible, e.g. {@code ROLE_DOC_OFFICER}. */
    @Column(name = "assigned_role", length = 60)
    private String assignedRole;

    /** Specific user assigned, if any. */
    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by", length = 255)
    private String completedBy;
}
