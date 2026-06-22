package com.eduflow.university;

import com.eduflow.common.BaseEntity;
import com.eduflow.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

/**
 * A university curated by a tenant consultancy. Tenant-scoped master data that
 * {@link Course}s — and ultimately applications — reference.
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "universities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class University extends BaseEntity {

    /** The consultancy this university belongs to. Never null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "website", length = 255)
    private String website;

    /** Short reference code (e.g. internal shorthand). Optional. */
    @Column(name = "code", length = 50)
    private String code;

    /** Soft enable/disable instead of hard delete. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
