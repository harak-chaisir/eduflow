package com.eduflow.workflow;

import com.eduflow.common.BaseEntity;
import com.eduflow.document.DocumentType;
import com.eduflow.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A single step within a {@link WorkflowTemplate} (PRD §7 "Stage Configuration").
 *
 * <p>Carries presentation (name, code, colour, order), behaviour (stage type), an SLA
 * target in days, the owner {@code role}, and the set of {@link DocumentType}s a student
 * must have approved before they can leave this stage (PRD §10).</p>
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "workflow_stages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_template_id", nullable = false)
    private WorkflowTemplate template;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Stable machine code, unique within a template (e.g. {@code DOC_COLLECTION}). */
    @Column(name = "code", nullable = false, length = 60)
    private String code;

    /** Position in the workflow; lower comes first. The lowest is the entry stage. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "description", length = 1000)
    private String description;

    /** UI accent colour for this stage (hex or token). */
    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** SLA target in days; {@code null} = no SLA tracked for this stage. */
    @Column(name = "sla_days")
    private Integer slaDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false, length = 40)
    @Builder.Default
    private StageType stageType = StageType.NORMAL;

    /** Role responsible for work in this stage, e.g. {@code ROLE_DOC_OFFICER}. Optional. */
    @Column(name = "owner_role", length = 60)
    private String ownerRole;

    /** Documents that must be APPROVED before a student can progress past this stage. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "workflow_stage_required_documents",
            joinColumns = @JoinColumn(name = "stage_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 60)
    @Builder.Default
    private List<DocumentType> requiredDocuments = new ArrayList<>();
}
