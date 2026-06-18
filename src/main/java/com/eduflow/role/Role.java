package com.eduflow.role;

import com.eduflow.common.BaseEntity;
import com.eduflow.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents an application role (e.g. {@code ROLE_SUPER_ADMIN}, {@code ROLE_COUNSELOR}).
 * Roles with a {@code null} tenant are system-wide; tenant-scoped roles belong to one consultancy.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    /** Spring Security authority name, e.g. {@code ROLE_COUNSELOR}. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Human-readable description of the role. */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Owning tenant, or {@code null} for platform-wide system roles.
     * Only {@code ROLE_SUPER_ADMIN} users may access data across tenant boundaries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;
}

