package com.eduflow.user;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.user.dto.TenantAdminSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Super-admin operations over tenant user accounts.
 *
 * <p>Currently powers the "reset admin password" action on the tenant detail screen:
 * a super admin sets a new password directly for a chosen tenant admin, activating the
 * account. Tenant scoping is enforced via {@link UserRepository#findByIdAndTenantId} so a
 * tampered {@code userId} can never reach another tenant's user.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final String TENANT_ADMIN_ROLE = "ROLE_TENANT_ADMIN";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** Lists a tenant's admin users (any status) for the password-reset picker. */
    @Transactional(readOnly = true)
    public List<TenantAdminSummary> listTenantAdmins(UUID tenantId) {
        return userRepository.findByTenantIdAndRoleName(tenantId, TENANT_ADMIN_ROLE).stream()
                .map(TenantAdminSummary::from)
                .toList();
    }

    /**
     * Sets a new BCrypt password for a tenant admin and activates the account.
     *
     * @throws IllegalArgumentException if the user is not found in the tenant or is not an admin
     */
    @Transactional
    public void setPassword(UUID tenantId, UUID userId, String rawPassword) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No such user in this tenant."));

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> TENANT_ADMIN_ROLE.equals(r.getName()));
        if (!isAdmin) {
            throw new IllegalArgumentException("Selected user is not an admin of this tenant.");
        }

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        userRepository.save(user);

        auditService.publish(tenantId, actingUserId(),
                AuditAction.USER_PASSWORD_RESET, "USER", userId);

        log.info("Super admin {} reset password for user {} (tenant {})",
                actingUserId(), userId, tenantId);
    }

    /** UUID of the acting (authenticated) super admin, resolved from the security context. */
    private UUID actingUserId() {
        return ((EduFlowUserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId();
    }
}
