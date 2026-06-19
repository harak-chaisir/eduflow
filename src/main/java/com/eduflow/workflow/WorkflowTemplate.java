package com.eduflow.workflow;

import com.eduflow.common.BaseEntity;
import com.eduflow.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A reusable, tenant-scoped workflow definition (PRD §4 "Workflow Template").
 *
 * <p>A template owns an ordered list of {@link WorkflowStage}s connected by
 * {@link WorkflowTransition}s. Tenant admins build and version templates without code
 * changes; students are later assigned a template, creating an execution instance.</p>
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "workflow_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowTemplate extends BaseEntity {

    /** The consultancy this template belongs to. Never null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "country", length = 100)
    private String country;

    /** Version number; incremented when the template is cloned (PRD FR-1/FR-2). */
    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Whether new students are auto-assigned this template. Single default per tenant. */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultTemplate = false;

    /** Archived templates block new assignments; running instances continue (PRD FR-9). */
    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private boolean archived = false;

    /** Stages in display order. Cascade-managed so a template owns its definition graph. */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<WorkflowStage> stages = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkflowTransition> transitions = new ArrayList<>();
}
