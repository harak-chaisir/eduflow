package com.eduflow.user;

import com.eduflow.common.BaseEntity;
import com.eduflow.role.Role;
import com.eduflow.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * A staff user belonging to a tenant on the EduFlow platform.
 * Each user holds one or more {@link Role roles} which govern their capabilities.
 *
 * <p>⚠️ Never expose {@code passwordHash} in any response DTO or API response.</p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    /** Login email address. Unique within a tenant. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** BCrypt-hashed password. Never serialise this field into a response. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /** The consultancy this user belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Account lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /** Whether the user has confirmed their email address. */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    /** Roles granted to this user. Loaded lazily; eagerly initialised in the security service. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /** Convenience helper: returns the user's full name. */
    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }
}

