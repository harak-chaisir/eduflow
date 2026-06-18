package com.eduflow.tenant;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.dto.*;
import com.eduflow.tenant.event.TenantEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Core service for the Tenant Management module (PRD §6–§9, §12).
 *
 * <p>Platform-level operations (provision, list, status, plan) are restricted to
 * {@code SUPER_ADMIN} at the controller. Profile/settings operations are allowed for
 * a tenant's own {@code TENANT_ADMIN}; "own tenant" is verified here against the
 * authenticated principal via {@link #assertCanAccess(UUID)} — never a path param.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TenantService {

    private static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository settingsRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    // ── Provisioning ───────────────────────────────────────────────────────────

    /**
     * Provisions a new tenant: validates the slug, seeds plan limits and a settings
     * row in a single transaction, then publishes {@link TenantEvents.TenantCreated}.
     * The first-admin invite runs in an {@code AFTER_COMMIT} listener, so a rolled-back
     * creation never sends an invite. The Drive root stays null (lazy — see PRD §7).
     */
    public TenantResponse provision(CreateTenantRequest request) {
        if (tenantRepository.existsBySlug(request.getSlug())) {
            throw new DuplicateSlugException(request.getSlug());
        }

        TenantPlan plan = request.getPlan();
        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .status(TenantStatus.ACTIVE)
                .plan(plan)
                .maxStudents(request.getMaxStudents() != null
                        ? request.getMaxStudents() : plan.defaultMaxStudents())
                .maxStaffUsers(request.getMaxStaffUsers() != null
                        ? request.getMaxStaffUsers() : plan.defaultMaxStaffUsers())
                .primaryContactName(request.getPrimaryContactName())
                .primaryContactEmail(request.getPrimaryContactEmail())
                .primaryContactPhone(request.getPrimaryContactPhone())
                .locale(request.getLocale() != null ? request.getLocale() : "en-NP")
                .timezone(request.getTimezone() != null ? request.getTimezone() : "Asia/Kathmandu")
                .build();

        Tenant saved = tenantRepository.save(tenant);

        // Seed the 1:1 settings row in the same transaction (PRD §7.3).
        TenantSettings settings = TenantSettings.builder()
                .tenant(saved)
                .defaultNotificationChannels("EMAIL")
                .build();
        settingsRepository.save(settings);

        log.info("Provisioned tenant '{}' (slug={}, plan={}) id={}",
                saved.getName(), saved.getSlug(), saved.getPlan(), saved.getId());

        events.publishEvent(new TenantEvents.TenantCreated(
                saved.getId(), resolvedUserId(), saved.getName(),
                saved.getPrimaryContactName(), saved.getPrimaryContactEmail()));

        return TenantResponse.from(saved);
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TenantResponse> search(TenantSearchCriteria criteria, Pageable pageable) {
        return tenantRepository
                .findAll(TenantSpecification.from(criteria), pageable)
                .map(TenantResponse::from);
    }

    @Transactional(readOnly = true)
    public TenantResponse getById(UUID id) {
        assertCanAccess(id);
        return TenantResponse.from(findOrThrow(id));
    }

    /** Returns the profile of the calling user's own tenant. */
    @Transactional(readOnly = true)
    public TenantResponse getCurrent() {
        return TenantResponse.from(findOrThrow(resolvedTenantId()));
    }

    @Transactional(readOnly = true)
    public TenantSettingsResponse getSettings(UUID id) {
        assertCanAccess(id);
        return TenantSettingsResponse.from(findSettingsOrThrow(id));
    }

    // ── Profile / settings updates ───────────────────────────────────────────────

    /** Updates editable profile fields (name, contact, locale). Slug is immutable. */
    public TenantResponse updateProfile(UUID id, UpdateTenantProfileRequest req) {
        assertCanAccess(id);
        Tenant tenant = findOrThrow(id);

        if (req.getName() != null)                tenant.setName(req.getName());
        if (req.getPrimaryContactName() != null)  tenant.setPrimaryContactName(req.getPrimaryContactName());
        if (req.getPrimaryContactEmail() != null) tenant.setPrimaryContactEmail(req.getPrimaryContactEmail());
        if (req.getPrimaryContactPhone() != null) tenant.setPrimaryContactPhone(req.getPrimaryContactPhone());
        if (req.getLocale() != null)              tenant.setLocale(req.getLocale());
        if (req.getTimezone() != null)            tenant.setTimezone(req.getTimezone());

        Tenant saved = tenantRepository.save(tenant);
        auditService.publish(id, resolvedUserId(), AuditAction.TENANT_UPDATED, "TENANT", id);
        return TenantResponse.from(saved);
    }

    public TenantSettingsResponse updateSettings(UUID id, UpdateTenantSettingsRequest req) {
        assertCanAccess(id);
        TenantSettings settings = findSettingsOrThrow(id);

        if (req.getBrandColor() != null)                 settings.setBrandColor(req.getBrandColor());
        if (req.getLogoReference() != null)              settings.setLogoReference(req.getLogoReference());
        if (req.getDefaultNotificationChannels() != null) settings.setDefaultNotificationChannels(req.getDefaultNotificationChannels());
        if (req.getDefaultWorkflowTemplateId() != null)  settings.setDefaultWorkflowTemplateId(req.getDefaultWorkflowTemplateId());
        if (req.getRequiredDocumentsOverride() != null)  settings.setRequiredDocumentsOverride(req.getRequiredDocumentsOverride());

        TenantSettings saved = settingsRepository.save(settings);
        auditService.publish(id, resolvedUserId(), AuditAction.TENANT_SETTINGS_UPDATED, "TENANT", id);
        return TenantSettingsResponse.from(saved);
    }

    // ── Lifecycle (super-admin) ──────────────────────────────────────────────────

    /**
     * Changes a tenant's lifecycle status, validating the move against
     * {@link TenantStatus#canTransitionTo}. SUSPENDED requires a reason; lifecycle
     * stamps are set on entry and cleared on reactivation.
     */
    public TenantResponse changeStatus(UUID id, ChangeTenantStatusRequest req) {
        Tenant tenant = findOrThrow(id);
        TenantStatus current = tenant.getStatus();
        TenantStatus target = req.getStatus();

        if (!current.canTransitionTo(target)) {
            throw new InvalidTenantStatusTransitionException(current, target);
        }
        if (target == TenantStatus.SUSPENDED
                && (req.getReason() == null || req.getReason().isBlank())) {
            throw new IllegalArgumentException("A reason is required when suspending a tenant");
        }

        switch (target) {
            case SUSPENDED -> {
                tenant.setSuspendedAt(Instant.now());
                tenant.setSuspensionReason(req.getReason());
            }
            case INACTIVE -> tenant.setDeactivatedAt(Instant.now());
            case ACTIVE -> {
                tenant.setSuspendedAt(null);
                tenant.setSuspensionReason(null);
                tenant.setDeactivatedAt(null);
            }
        }
        tenant.setStatus(target);
        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant {} status changed {} → {}", id, current, target);

        events.publishEvent(new TenantEvents.TenantStatusChanged(
                id, resolvedUserId(), current, target, req.getReason()));

        return TenantResponse.from(saved);
    }

    /** Changes a tenant's plan; limits reset to the new plan's defaults unless overridden. */
    public TenantResponse changePlan(UUID id, ChangeTenantPlanRequest req) {
        Tenant tenant = findOrThrow(id);
        TenantPlan previous = tenant.getPlan();
        TenantPlan target = req.getPlan();

        tenant.setPlan(target);
        tenant.setMaxStudents(req.getMaxStudents() != null
                ? req.getMaxStudents() : target.defaultMaxStudents());
        tenant.setMaxStaffUsers(req.getMaxStaffUsers() != null
                ? req.getMaxStaffUsers() : target.defaultMaxStaffUsers());

        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant {} plan changed {} → {}", id, previous, target);

        events.publishEvent(new TenantEvents.TenantPlanChanged(
                id, resolvedUserId(), previous, target));

        return TenantResponse.from(saved);
    }

    /** Requests an additional tenant admin (the user module handles the invite AFTER_COMMIT). */
    public void inviteAdmin(UUID id, InviteTenantAdminRequest req) {
        // Ensure the tenant exists before publishing the invite request.
        findOrThrow(id);
        events.publishEvent(new TenantEvents.TenantAdminInvited(id, resolvedUserId(), req.getEmail()));
        log.info("Requested additional tenant admin invite for tenant {} ({})", id, req.getEmail());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private Tenant findOrThrow(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id));
    }

    private TenantSettings findSettingsOrThrow(UUID id) {
        return settingsRepository.findByTenantId(id)
                .orElseThrow(() -> new TenantNotFoundException(id));
    }

    /**
     * Allows the action when the caller is a super admin or is acting on their own
     * tenant. Prevents a tenant admin from addressing another tenant's id.
     */
    private void assertCanAccess(UUID tenantId) {
        if (isSuperAdmin()) {
            return;
        }
        if (!tenantId.equals(resolvedTenantId())) {
            throw new AccessDeniedException("You may only access your own tenant");
        }
    }

    private boolean isSuperAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SUPER_ADMIN::equals);
    }

    private UUID resolvedTenantId() {
        return principal().getTenantId();
    }

    private UUID resolvedUserId() {
        return principal().getUserId();
    }

    private EduFlowUserDetails principal() {
        return (EduFlowUserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
