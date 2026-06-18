package com.eduflow.tenant;

import com.eduflow.tenant.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for the Tenant Management module (PRD §14).
 *
 * <p>Base path: {@code /api/v1/tenants}. Platform-level operations are restricted to
 * {@code SUPER_ADMIN}; profile/settings reads and writes additionally allow a tenant's
 * own {@code TENANT_ADMIN}, with "own tenant" verified in the service against the
 * authenticated principal.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    // ── List (super-admin) ───────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<TenantResponse>> listTenants(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) TenantPlan plan,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {

        TenantSearchCriteria criteria = TenantSearchCriteria.builder()
                .q(q).status(status).plan(plan).build();
        return ResponseEntity.ok(tenantService.search(criteria, pageable));
    }

    // ── Create (super-admin) ─────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody CreateTenantRequest request) {

        TenantResponse response = tenantService.provision(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    // ── Current tenant (any tenant user) ─────────────────────────────────────────

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TenantResponse> getCurrentTenant() {
        return ResponseEntity.ok(tenantService.getCurrent());
    }

    // ── Get one (super-admin or own tenant-admin) ────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantProfileRequest request) {
        return ResponseEntity.ok(tenantService.updateProfile(id, request));
    }

    // ── Status / plan (super-admin) ──────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeTenantStatusRequest request) {
        return ResponseEntity.ok(tenantService.changeStatus(id, request));
    }

    @PatchMapping("/{id}/plan")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantResponse> changePlan(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeTenantPlanRequest request) {
        return ResponseEntity.ok(tenantService.changePlan(id, request));
    }

    // ── Settings (super-admin or own tenant-admin) ───────────────────────────────

    @GetMapping("/{id}/settings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
    public ResponseEntity<TenantSettingsResponse> getSettings(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getSettings(id));
    }

    @PatchMapping("/{id}/settings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
    public ResponseEntity<TenantSettingsResponse> updateSettings(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantSettingsRequest request) {
        return ResponseEntity.ok(tenantService.updateSettings(id, request));
    }

    // ── Invite additional admin (super-admin) ────────────────────────────────────

    @PostMapping("/{id}/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> inviteAdmin(
            @PathVariable UUID id,
            @Valid @RequestBody InviteTenantAdminRequest request) {
        tenantService.inviteAdmin(id, request);
        return ResponseEntity.accepted().build();
    }
}
