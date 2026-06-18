package com.eduflow.user;

import com.eduflow.role.Role;
import com.eduflow.role.RoleRepository;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantLimitService;
import com.eduflow.tenant.TenantNotFoundException;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.tenant.event.TenantEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * Creates {@code TENANT_ADMIN} accounts in response to tenant lifecycle events
 * (PRD §7 step 5, §14 {@code POST /tenants/{id}/admins}).
 *
 * <p>Runs {@code AFTER_COMMIT} so a rolled-back tenant creation never provisions an
 * admin, and in a {@code REQUIRES_NEW} transaction since the original tx has already
 * committed. The invite e-mail is currently <b>stubbed</b> — the set-password link is
 * logged. Swap the log line for a real {@code NotificationService} once that lands.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAdminInviteListener {

    private static final String TENANT_ADMIN_ROLE = "ROLE_TENANT_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final TenantLimitService tenantLimitService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;

    /** Fallback base URL for invite links when no HTTP request context is available. */
    @Value("${eduflow.app.base-url:http://localhost:8080}")
    private String fallbackBaseUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTenantCreated(TenantEvents.TenantCreated e) {
        inviteAdmin(e.tenantId(), e.primaryContactEmail(), e.primaryContactName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAdminInvited(TenantEvents.TenantAdminInvited e) {
        inviteAdmin(e.tenantId(), e.email(), null);
    }

    /**
     * Creates a {@code PENDING_VERIFICATION} tenant admin and logs the invite link.
     * Idempotent: a no-op if the email already exists in the tenant.
     */
    private void inviteAdmin(UUID tenantId, String email, String fullName) {
        if (email == null || email.isBlank()) {
            log.warn("Skipping tenant-admin invite for tenant {} — no email provided", tenantId);
            return;
        }
        if (userRepository.existsByEmailIgnoreCaseAndTenantId(email, tenantId)) {
            log.info("Tenant-admin invite skipped — {} already exists in tenant {}", email, tenantId);
            return;
        }

        tenantLimitService.assertCanAddStaff(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        Role adminRole = roleRepository.findByName(TENANT_ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing system role " + TENANT_ADMIN_ROLE));

        String[] names = splitName(fullName);
        // Unusable random hash until the invitee sets their own password via the token.
        String placeholderHash = passwordEncoder.encode(UUID.randomUUID().toString());

        User admin = User.builder()
                .email(email)
                .passwordHash(placeholderHash)
                .firstName(names[0])
                .lastName(names[1])
                .tenant(tenant)
                .status(UserStatus.PENDING_VERIFICATION)
                .emailVerified(false)
                .build();
        admin.getRoles().add(adminRole);

        User saved = userRepository.save(admin);

        // Issue a real, single-use set-password token and print a working link.
        // TODO: replace the console log with a real NotificationService email.
        String inviteToken = passwordResetService.createToken(saved);
        String link = baseUrl() + "/set-password?token=" + inviteToken;
        log.info("""

                ───────────────────────────────────────────────────────────────
                 Tenant admin invite for {} (tenant {})
                 Account created in PENDING_VERIFICATION — set a password to activate.
                 Set-password link:
                   {}
                ───────────────────────────────────────────────────────────────""",
                email, tenantId, link);
    }

    /**
     * Resolves the application base URL from the current HTTP request when available
     * (so the link matches the host the operator is actually using), falling back to
     * the configured {@code eduflow.app.base-url}.
     */
    private String baseUrl() {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (IllegalStateException noRequestContext) {
            return fallbackBaseUrl;
        }
    }

    /** Splits a display name into [first, last]; tolerates null/single-token names. */
    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"Tenant", "Admin"};
        }
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }
}
