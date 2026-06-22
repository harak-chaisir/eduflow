package com.eduflow.university;

import com.eduflow.common.BaseEntity;
import com.eduflow.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A course offered by a {@link University}. Tenant-scoped master data; the tenant
 * is denormalized onto the row so courses can be queried directly without joining
 * through the university.
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course extends BaseEntity {

    /** The consultancy this course belongs to. Denormalized for tenant-scoped queries. Never null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** The university offering this course. Never null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 50)
    private CourseLevel level;

    /** Intake month, 1–12. Optional. */
    @Column(name = "intake_month")
    private Integer intakeMonth;

    @Column(name = "intake_year")
    private Integer intakeYear;

    @Column(name = "tuition_fee", precision = 12, scale = 2)
    private BigDecimal tuitionFee;

    @Column(name = "entry_requirements", length = 1000)
    private String entryRequirements;

    /** Soft enable/disable instead of hard delete. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
