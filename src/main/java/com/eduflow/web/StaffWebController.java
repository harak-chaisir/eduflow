package com.eduflow.web;

import com.eduflow.user.DuplicateStaffException;
import com.eduflow.user.LastTenantAdminException;
import com.eduflow.user.StaffNotFoundException;
import com.eduflow.user.StaffService;
import com.eduflow.user.UserStatus;
import com.eduflow.user.dto.InviteStaffRequest;
import com.eduflow.user.dto.StaffInviteResult;
import com.eduflow.user.dto.StaffResponse;
import com.eduflow.user.dto.StaffRosterRow;
import com.eduflow.user.dto.StaffSearchCriteria;
import com.eduflow.user.dto.UpdateStaffRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.HashSet;
import java.util.UUID;

/**
 * Thymeleaf web controller for tenant staff management.
 *
 * <p>Handles page rendering at {@code /staff/**}. All data access goes through
 * {@link StaffService}; the tenant is resolved from the security context inside the
 * service. Restricted to tenant administrators (and platform super admins).</p>
 */
@Slf4j
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
public class StaffWebController {

    private final StaffService staffService;

    // ── List / Search ────────────────────────────────────────────────────────

    /** Renders the paginated staff roster: stat strip, filters, and table. */
    @GetMapping
    public String listStaff(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        model.addAttribute("stats", staffService.getStaffStats());
        populateSearchModel(model, name, email, status, role, page, size);
        return "staff/list";
    }

    /** HTMX live-search endpoint — returns only the {@code staffResults} fragment. */
    @GetMapping("/search")
    public String searchStaff(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        populateSearchModel(model, name, email, status, role, page, size);
        return "staff/list :: staffResults";
    }

    private void populateSearchModel(Model model, String name, String email,
                                     UserStatus status, String role, int page, int size) {
        StaffSearchCriteria criteria = StaffSearchCriteria.builder()
                .name(name).email(email).status(status).role(emptyToNull(role)).build();

        Page<StaffRosterRow> staffPage = staffService.searchStaffRoster(
                criteria, PageRequest.of(page, size, Sort.by("firstName").ascending()));

        model.addAttribute("staffPage", staffPage);
        model.addAttribute("searchName", name);
        model.addAttribute("searchEmail", email);
        model.addAttribute("searchStatus", status);
        model.addAttribute("searchRole", role);
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("roleOptions", staffService.listAssignableRoles());
        model.addAttribute("currentSize", size);
    }

    // ── Invite (create) ──────────────────────────────────────────────────────

    /** Renders the blank staff invite form. */
    @GetMapping("/new")
    public String showInviteForm(Model model) {
        model.addAttribute("staffForm", new StaffFormData());
        addFormReferenceData(model, false);
        return "staff/form";
    }

    /** Handles the staff invite form submission. */
    @PostMapping("/new")
    public String inviteStaff(
            @Valid @ModelAttribute("staffForm") StaffFormData form,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            addFormReferenceData(model, false);
            return "staff/form";
        }

        try {
            StaffInviteResult invite = staffService.inviteStaff(toInviteRequest(form));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Invited " + invite.staff().getFullName().trim()
                            + ". Share this set-password link with them: " + setPasswordLink(invite.token()));
            return "redirect:/staff/" + invite.staff().getId();
        } catch (DuplicateStaffException ex) {
            addFormReferenceData(model, false);
            model.addAttribute("errorMessage", ex.getMessage());
            return "staff/form";
        }
    }

    // ── Detail ───────────────────────────────────────────────────────────────

    /** Renders the staff detail page: profile, caseload, activity, access, and record info. */
    @GetMapping("/{id}")
    public String viewStaff(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("staff", staffService.getStaff(id));
            model.addAttribute("caseload", staffService.getCaseload(id));
            model.addAttribute("activity", staffService.getRecentActivity(id));
            model.addAttribute("permissions", staffService.getPermissions(id));
            return "staff/detail";
        } catch (StaffNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/staff";
        }
    }

    // ── Edit ─────────────────────────────────────────────────────────────────

    /** Renders the edit form pre-populated with current staff data. */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        try {
            StaffResponse staff = staffService.getStaff(id);
            model.addAttribute("staffForm", toFormData(staff));
            addFormReferenceData(model, true);
            model.addAttribute("staffId", id);
            model.addAttribute("staffName", staff.getFullName().trim());
            return "staff/form";
        } catch (StaffNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/staff";
        }
    }

    /** Handles the edit form submission. */
    @PostMapping("/{id}/edit")
    public String updateStaff(
            @PathVariable UUID id,
            @Valid @ModelAttribute("staffForm") StaffFormData form,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            addFormReferenceData(model, true);
            model.addAttribute("staffId", id);
            return "staff/form";
        }

        try {
            StaffResponse staff = staffService.updateStaff(id, toUpdateRequest(form));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Staff member '" + staff.getFullName().trim() + "' was updated.");
            return "redirect:/staff/" + id;
        } catch (LastTenantAdminException | IllegalArgumentException ex) {
            addFormReferenceData(model, true);
            model.addAttribute("staffId", id);
            model.addAttribute("errorMessage", ex.getMessage());
            return "staff/form";
        }
    }

    // ── Activate / deactivate ─────────────────────────────────────────────────

    /** Activates or deactivates a staff member (HTMX-aware). */
    @PostMapping("/{id}/status")
    public String updateStatus(
            @PathVariable UUID id,
            @RequestParam UserStatus newStatus,
            @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            StaffResponse staff = staffService.setStatus(id, newStatus);
            if (htmxRequest != null) {
                model.addAttribute("staff", staff);
                return "staff/detail :: statusCard";
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Status changed to " + newStatus + " for " + staff.getFullName().trim() + ".");
        } catch (LastTenantAdminException | IllegalArgumentException | StaffNotFoundException ex) {
            if (htmxRequest != null) {
                try {
                    model.addAttribute("staff", staffService.getStaff(id));
                } catch (StaffNotFoundException notFound) {
                    // Swallow — redirect handles it below.
                }
                model.addAttribute("errorMessage", ex.getMessage());
                return "staff/detail :: statusCard";
            }
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/" + id;
    }

    // ── Reset password ─────────────────────────────────────────────────────────

    /** Issues a fresh set-password link for a staff member. */
    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            String token = staffService.issuePasswordReset(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "New set-password link issued: " + setPasswordLink(token));
        } catch (StaffNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/" + id;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void addFormReferenceData(Model model, boolean isEdit) {
        model.addAttribute("assignableRoles", staffService.listAssignableRoles());
        model.addAttribute("isEdit", isEdit);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String setPasswordLink(String token) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/set-password")
                .queryParam("token", token)
                .toUriString();
    }

    private InviteStaffRequest toInviteRequest(StaffFormData form) {
        return InviteStaffRequest.builder()
                .email(form.getEmail())
                .firstName(form.getFirstName())
                .lastName(form.getLastName())
                .roleNames(new HashSet<>(form.getRoleNames()))
                .build();
    }

    private UpdateStaffRequest toUpdateRequest(StaffFormData form) {
        return UpdateStaffRequest.builder()
                .firstName(form.getFirstName())
                .lastName(form.getLastName())
                .roleNames(new HashSet<>(form.getRoleNames()))
                .build();
    }

    private StaffFormData toFormData(StaffResponse staff) {
        StaffFormData form = new StaffFormData();
        form.setEmail(staff.getEmail());
        form.setFirstName(staff.getFirstName());
        form.setLastName(staff.getLastName());
        form.setRoleNames(new java.util.ArrayList<>(staff.getRoleNames()));
        return form;
    }
}
