package com.eduflow.web;

import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.NotificationChannel;
import com.eduflow.tenant.TenantNotFoundException;
import com.eduflow.tenant.TenantPlan;
import com.eduflow.tenant.TenantStatus;
import com.eduflow.tenant.TenantService;
import com.eduflow.tenant.dto.*;
import com.eduflow.user.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * Thymeleaf web controller for the Tenant Management module.
 *
 * <p>The super-admin "Tenants" screen lives under {@code /tenants}; the tenant-admin
 * "Workspace settings" screen lives under {@code /workspace}. All data access goes
 * through {@link TenantService}, which resolves and authorises the tenant from the
 * security context.</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TenantWebController {

    /** Minimum password length, kept in sync with the public set-password flow. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final TenantService tenantService;
    private final UserAdminService userAdminService;

    // ── Super-admin: list ────────────────────────────────────────────────────────

    @GetMapping("/tenants")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String listTenants(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) TenantPlan plan,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model,
            Authentication auth) {

        addNavAttributes(model, auth);
        populateListModel(model, q, status, plan, page, size);
        return "tenant/list";
    }

    /** HTMX live-search endpoint — returns only the {@code tenantResults} fragment. */
    @GetMapping("/tenants/search")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String searchTenants(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) TenantPlan plan,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        populateListModel(model, q, status, plan, page, size);
        return "tenant/list :: tenantResults";
    }

    // ── Super-admin: create ──────────────────────────────────────────────────────

    @GetMapping("/tenants/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String showCreateForm(Model model, Authentication auth) {
        addNavAttributes(model, auth);
        model.addAttribute("tenantForm", CreateTenantRequest.builder().plan(TenantPlan.STARTER).build());
        model.addAttribute("plans", TenantPlan.values());
        return "tenant/form";
    }

    @PostMapping("/tenants/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String createTenant(
            @Valid @ModelAttribute("tenantForm") CreateTenantRequest form,
            BindingResult result,
            Model model,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            addNavAttributes(model, auth);
            model.addAttribute("plans", TenantPlan.values());
            return "tenant/form";
        }
        try {
            TenantResponse tenant = tenantService.provision(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tenant '" + tenant.getName() + "' was provisioned. An admin invite was sent to "
                            + tenant.getPrimaryContactEmail() + ".");
            return "redirect:/tenants/" + tenant.getId();
        } catch (RuntimeException ex) {
            addNavAttributes(model, auth);
            model.addAttribute("plans", TenantPlan.values());
            model.addAttribute("errorMessage", ex.getMessage());
            return "tenant/form";
        }
    }

    // ── Super-admin: detail + lifecycle ──────────────────────────────────────────

    @GetMapping("/tenants/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String viewTenant(@PathVariable UUID id, Model model, Authentication auth,
                             RedirectAttributes redirectAttributes) {
        addNavAttributes(model, auth);
        try {
            populateDetailModel(model, id, null, null);
            return "tenant/detail";
        } catch (TenantNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/tenants";
        }
    }

    @PostMapping("/tenants/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String changeStatus(@PathVariable UUID id,
                               @RequestParam TenantStatus status,
                               @RequestParam(required = false) String reason,
                               @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        String message = null, error = null;
        try {
            tenantService.changeStatus(id, ChangeTenantStatusRequest.builder()
                    .status(status).reason(reason).build());
            message = "Status changed to " + status + ".";
        } catch (RuntimeException ex) {
            error = ex.getMessage();
        }
        if (htmxRequest != null) {
            populateDetailModel(model, id, message, error);
            return "tenant/detail :: tenantPanels";
        }
        redirectAttributes.addFlashAttribute(message != null ? "successMessage" : "errorMessage",
                message != null ? message : error);
        return "redirect:/tenants/" + id;
    }

    @PostMapping("/tenants/{id}/plan")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String changePlan(@PathVariable UUID id,
                             @RequestParam TenantPlan plan,
                             @RequestParam(required = false) Integer maxStudents,
                             @RequestParam(required = false) Integer maxStaffUsers,
                             @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        String message = null, error = null;
        try {
            tenantService.changePlan(id, ChangeTenantPlanRequest.builder()
                    .plan(plan).maxStudents(maxStudents).maxStaffUsers(maxStaffUsers).build());
            message = "Plan changed to " + plan + ".";
        } catch (RuntimeException ex) {
            error = ex.getMessage();
        }
        if (htmxRequest != null) {
            populateDetailModel(model, id, message, error);
            return "tenant/detail :: tenantPanels";
        }
        redirectAttributes.addFlashAttribute(message != null ? "successMessage" : "errorMessage",
                message != null ? message : error);
        return "redirect:/tenants/" + id;
    }

    @PostMapping("/tenants/{id}/reset-password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String resetAdminPassword(@PathVariable UUID id,
                                     @RequestParam UUID userId,
                                     @RequestParam String password,
                                     @RequestParam String confirmPassword,
                                     @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        String message = null, error = validatePassword(password, confirmPassword);
        if (error == null) {
            try {
                userAdminService.setPassword(id, userId, password);
                message = "Password updated.";
            } catch (RuntimeException ex) {
                error = ex.getMessage();
            }
        }
        if (htmxRequest != null) {
            populateDetailModel(model, id, message, error);
            return "tenant/detail :: tenantPanels";
        }
        redirectAttributes.addFlashAttribute(message != null ? "successMessage" : "errorMessage",
                message != null ? message : error);
        return "redirect:/tenants/" + id;
    }

    // ── Tenant-admin: own workspace ──────────────────────────────────────────────

    @GetMapping("/workspace")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
    public String workspace(Model model, Authentication auth) {
        addNavAttributes(model, auth);
        UUID tenantId = ((EduFlowUserDetails) auth.getPrincipal()).getTenantId();
        populateWorkspaceModel(model, tenantId);
        return "tenant/workspace";
    }

    @PostMapping("/workspace/profile")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
    public String updateProfile(@ModelAttribute UpdateTenantProfileRequest form,
                                @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                                Authentication auth,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        UUID tenantId = ((EduFlowUserDetails) auth.getPrincipal()).getTenantId();
        String message = null, error = null;
        try {
            tenantService.updateProfile(tenantId, form);
            message = "Workspace profile updated.";
        } catch (RuntimeException ex) {
            error = ex.getMessage();
        }
        if (htmxRequest != null) {
            populateWorkspaceModel(model, tenantId);
            model.addAttribute("profileMessage", message);
            model.addAttribute("profileError", error);
            return "tenant/workspace :: profileCard";
        }
        redirectAttributes.addFlashAttribute(message != null ? "profileMessage" : "profileError",
                message != null ? message : error);
        return "redirect:/workspace";
    }

    @PostMapping("/workspace/settings")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
    public String updateSettings(@ModelAttribute UpdateTenantSettingsRequest form,
                                 @RequestParam(value = "logoFile", required = false) MultipartFile logoFile,
                                 @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                                 Authentication auth,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        UUID tenantId = ((EduFlowUserDetails) auth.getPrincipal()).getTenantId();
        String message = null, error = null;
        try {
            if (logoFile != null && !logoFile.isEmpty()) {
                tenantService.updateLogo(tenantId, logoFile);
            }
            tenantService.updateSettings(tenantId, form);
            message = "Workspace settings updated.";
        } catch (RuntimeException ex) {
            error = ex.getMessage();
        }
        if (htmxRequest != null) {
            populateWorkspaceModel(model, tenantId);
            model.addAttribute("settingsMessage", message);
            model.addAttribute("settingsError", error);
            return "tenant/workspace :: settingsCard";
        }
        redirectAttributes.addFlashAttribute(message != null ? "settingsMessage" : "settingsError",
                message != null ? message : error);
        return "redirect:/workspace";
    }

    /** Serves the authenticated tenant's stored logo image (404 if none uploaded). */
    @GetMapping("/workspace/logo")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Resource> workspaceLogo(Authentication auth) {
        UUID tenantId = ((EduFlowUserDetails) auth.getPrincipal()).getTenantId();
        Optional<Resource> logo = tenantService.loadLogo(tenantId);
        if (logo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = logo.get();
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noCache())
                .body(resource);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void addNavAttributes(Model model, Authentication auth) {
        EduFlowUserDetails principal = (EduFlowUserDetails) auth.getPrincipal();
        model.addAttribute("fullName", principal.getFullName().trim());
        model.addAttribute("email", principal.getUsername());
    }

    /** Populates the tenant-list model (results page + filter state) shared by list & search. */
    private void populateListModel(Model model, String q, TenantStatus status, TenantPlan plan,
                                   int page, int size) {
        TenantSearchCriteria criteria = TenantSearchCriteria.builder()
                .q(q).status(status).plan(plan).build();
        Page<TenantResponse> tenantsPage = tenantService.search(
                criteria, PageRequest.of(page, size, Sort.by("name").ascending()));

        model.addAttribute("tenantsPage", tenantsPage);
        model.addAttribute("searchQ", q);
        model.addAttribute("searchStatus", status);
        model.addAttribute("searchPlan", plan);
        model.addAttribute("statuses", TenantStatus.values());
        model.addAttribute("plans", TenantPlan.values());
    }

    /** Populates the tenant-detail model (profile/lifecycle/plan panels) plus any flash message. */
    private void populateDetailModel(Model model, UUID id, String successMessage, String errorMessage) {
        model.addAttribute("tenant", tenantService.getById(id));
        model.addAttribute("settings", tenantService.getSettings(id));
        model.addAttribute("statuses", TenantStatus.values());
        model.addAttribute("plans", TenantPlan.values());
        model.addAttribute("tenantAdmins", userAdminService.listTenantAdmins(id));
        if (successMessage != null) model.addAttribute("successMessage", successMessage);
        if (errorMessage != null) model.addAttribute("errorMessage", errorMessage);
    }

    /** Mirrors the public set-password rules: min length and matching confirmation. */
    private String validatePassword(String password, String confirmPassword) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (!password.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        return null;
    }

    /** Populates the workspace model (profile + settings cards) shared by view & inline saves. */
    private void populateWorkspaceModel(Model model, UUID tenantId) {
        TenantSettingsResponse settings = tenantService.getSettings(tenantId);
        model.addAttribute("tenant", tenantService.getById(tenantId));
        model.addAttribute("settings", settings);
        model.addAttribute("channels", NotificationChannel.values());
        model.addAttribute("selectedChannel", selectedChannel(settings.getDefaultNotificationChannels()));
    }

    /** First saved channel from the stored CSV (e.g. {@code "SMS,EMAIL"} → {@code "SMS"}), or EMAIL. */
    private String selectedChannel(String csv) {
        if (csv != null && !csv.isBlank()) {
            for (String token : csv.split(",")) {
                String name = token.trim().toUpperCase();
                if (NotificationChannel.isValid(name)) {
                    return name;
                }
            }
        }
        return NotificationChannel.EMAIL.name();
    }
}
