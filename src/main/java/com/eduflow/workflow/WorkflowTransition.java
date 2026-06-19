package com.eduflow.workflow;

import com.eduflow.common.BaseEntity;
import com.eduflow.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

/**
 * A directed, allowed move between two {@link WorkflowStage}s of the same
 * {@link WorkflowTemplate} (PRD §9 "Transition Management").
 *
 * <p>The set of transitions forms the workflow graph. {@code StudentWorkflowService}
 * only permits a stage move when a matching transition exists; {@code MANUAL_APPROVAL}
 * transitions (or any with {@code requiresApproval}) additionally require the source
 * stage's owner role.</p>
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "workflow_transitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowTransition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_template_id", nullable = false)
    private WorkflowTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_stage_id", nullable = false)
    private WorkflowStage fromStage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_stage_id", nullable = false)
    private WorkflowStage toStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_type", nullable = false, length = 40)
    @Builder.Default
    private TransitionType transitionType = TransitionType.FORWARD;

    @Column(name = "label", length = 150)
    private String label;

    /** When true, only the source stage's owner role may perform this transition. */
    @Column(name = "requires_approval", nullable = false)
    @Builder.Default
    private boolean requiresApproval = false;
}
