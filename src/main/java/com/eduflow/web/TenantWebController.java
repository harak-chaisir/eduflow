package com.eduflow.web;

import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.TenantNotFoundException;
import com.eduflow.tenant.TenantPlan;
import com.eduflow.tenant.TenantStatus;
import com.eduflow.tenant.TenantService;
import com.eduflow.tenant.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    private final TenantService tenantService;

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
        return "tenant/list";
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
            model.addAttribute("tenant", tenantService.getById(id));
            model.addAttribute("settings", tenantService.getSettings(id));
            model.addAttribute("statuses", TenantStatus.values());
            model.addAttribute("plans", TenantPlan.values());
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
                               RedirectAttributes redirectAttributes) {
        try {
            tenantService.changeStatus(id, ChangeTenantStatusRequest.builder()
                    .status(status).reason(reason).build());
            redirectAttributes.addFlashAttribute("successMessage", "Status changed to " + status + ".");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/tenants/" + id;
    }

    @PostMapping("/tenants/{id}/plan")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String changePlan(@PathVariable UUID id,
                             @RequestParam TenantPlan plan,
                             @RequestParam(required = false) Integer maxStudents,
                             @RequestParam(required = false) Integer maxStaffUsers,
                             RedirectAttributes redirectAttributes) {
        try {
            tenantService.changePlan(id, ChangeTenantPlanRequest.builder()
                    .plan(plan).maxStudents(maxStudents).maxStaffUsers(maxStaffUsers).build());
            redirectAttributes.addFlashAttribute("successMessage", "Plan changed to " + plan + ".");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/tenants/" + id;
    }

    // ── Tenant-admin: own workspace ──────────────────────────────────────────────

    @GetMapping("/workspace")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
    public String workspace(Model model, Authentication auth) {
        addNavAttributes(model, auth);
        UUID tenantId = ((EduFlowUserDetails) auth.getPrincipal()).getTenantId();
        model.addAttribute("tenant", tenantService.getById(tenantId));
        model.addAttribute("settings", tenantService.getSettings(tenantId));
        return "tenant/workspace";
    }

    @PostMapping("/workspace/profile")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
    public String updateProfile(@ModelAttribute UpdateTenantProfileRequest form,
                                Authentication auth,
                                RedirectAttributes redirectAttributes) {
        UUID tenantId = ((EduFlowUserDetails) auth.getPrincipal()).getTenantId();
        try {
            tenantService.updateProfile(tenantId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Workspace profile updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workspace";
    }

    @PostMapping("/workspace/settings")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
    public String updateSettings(@ModelAttribute UpdateTenantSettingsRequest form,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        UUID tenantId = ((EduFlowUserDetails) auth.getPrincipal()).getTenantId();
        try {
            tenantService.updateSettings(tenantId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Workspace settings updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workspace";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void addNavAttributes(Model model, Authentication auth) {
        EduFlowUserDetails principal = (EduFlowUserDetails) auth.getPrincipal();
        model.addAttribute("fullName", principal.getFullName().trim());
        model.addAttribute("email", principal.getUsername());
    }
}
