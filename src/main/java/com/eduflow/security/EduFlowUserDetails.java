package com.eduflow.security;

import com.eduflow.tenant.TenantStatus;
import com.eduflow.user.User;
import com.eduflow.user.UserStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring Security {@link UserDetails} adapter for {@link User}.
 *
 * <p>Exposes {@link #getTenantId()} and {@link #getUserId()} so that service-layer
 * code can resolve the authenticated user's tenant without hitting the database again.</p>
 */
public class EduFlowUserDetails implements UserDetails {

    private final User user;

    /**
     * Tenant id and name are resolved eagerly in the constructor (while the
     * Hibernate session is still open) so they remain available in the view layer
     * after the session closes, without forcing a lazy initialization.
     */
    private final UUID tenantId;
    private final String tenantName;
    private final TenantStatus tenantStatus;

    public EduFlowUserDetails(User user) {
        this.user = user;
        this.tenantId = user.getTenant().getId();
        this.tenantName = user.getTenant().getName();
        this.tenantStatus = user.getTenant().getStatus();
    }

    // ── EduFlow-specific accessors ──────────────────────────────────────────────

    /** UUID of the tenant this user belongs to. */
    public UUID getTenantId() {
        return tenantId;
    }

    /** Display name of the tenant (consultancy) this user belongs to. */
    public String getTenantName() {
        return tenantName;
    }

    /** UUID of the authenticated user record. */
    public UUID getUserId() {
        return user.getId();
    }

    /** Full display name (firstName + lastName). */
    public String getFullName() {
        return user.getFullName().trim();
    }

    /** Returns the underlying {@link User} entity. */
    public User getUser() {
        return user;
    }

    // ── UserDetails ─────────────────────────────────────────────────────────────

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Returns the BCrypt-hashed password stored in the DB. */
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    /** Returns the user's email address, used as the username. */
    @Override
    @NonNull
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * The account is enabled only when the user is {@code ACTIVE} <em>and</em> their
     * tenant is {@code ACTIVE}. A {@code SUSPENDED}/{@code INACTIVE} tenant blocks all
     * its users at authentication (PRD §10.2); {@code SUPER_ADMIN} is exempt so the
     * platform operator can always operate.
     */
    @Override
    public boolean isEnabled() {
        if (user.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        return tenantStatus == TenantStatus.ACTIVE || isPlatformSuperAdmin();
    }

    /** True if this user holds the platform-level {@code ROLE_SUPER_ADMIN} authority. */
    private boolean isPlatformSuperAdmin() {
        return user.getRoles().stream()
                .anyMatch(role -> "ROLE_SUPER_ADMIN".equals(role.getName()));
    }
}




