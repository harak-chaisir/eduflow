package com.eduflow.web;

import com.eduflow.application.ApplicationNotFoundException;
import com.eduflow.application.ApplicationService;
import com.eduflow.application.ApplicationStatus;
import com.eduflow.application.DuplicateApplicationException;
import com.eduflow.application.InvalidApplicationStatusTransitionException;
import com.eduflow.application.dto.ApplicationResponse;
import com.eduflow.application.dto.ApplicationSearchCriteria;
import com.eduflow.application.dto.CreateApplicationRequest;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.StudentNotFoundException;
import com.eduflow.university.CourseNotFoundException;
import com.eduflow.university.CourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Thymeleaf web controller for the Applications pipeline and the student-detail
 * applications panel.
 *
 * <p>The tenant-wide pipeline lives at {@code /applications}; per-student create and
 * status actions are posted from the student detail page and re-render the
 * {@code applicationsPanel} fragment when called over HTMX.</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN','DOC_OFFICER','VISA_OFFICER')")
public class ApplicationWebController {

    private final ApplicationService applicationService;
    private final CourseService courseService;

    // ── Pipeline list ──────────────────────────────────────────────────────────

    @GetMapping("/applications")
    public String list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model, Authentication auth) {

        addNavAttributes(model, auth);
        populateSearchModel(model, status, page, size);
        return "applications/list";
    }

    @GetMapping("/applications/search")
    public String search(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        populateSearchModel(model, status, page, size);
        return "applications/list :: applicationResults";
    }

    private void populateSearchModel(Model model, ApplicationStatus status, int page, int size) {
        ApplicationSearchCriteria criteria = ApplicationSearchCriteria.builder().status(status).build();
        Page<ApplicationResponse> applicationsPage = applicationService.search(
                criteria, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        model.addAttribute("applicationsPage", applicationsPage);
        model.addAttribute("searchStatus", status);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("currentSize", size);
    }

    // ── Apply (from student detail) ──────────────────────────────────────────────

    @PostMapping("/students/{studentId}/applications")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String apply(
            @PathVariable UUID studentId,
            @RequestParam UUID courseId,
            @RequestParam(required = false) String notes,
            @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
            Model model, RedirectAttributes redirectAttributes) {

        try {
            CreateApplicationRequest request = CreateApplicationRequest.builder()
                    .courseId(courseId).notes(blankToNull(notes)).build();
            ApplicationResponse application = applicationService.createApplication(studentId, request);
            String msg = "Applied to '" + application.getCourseName() + "'.";
            if (htmxRequest != null) {
                model.addAttribute("successMessage", msg);
                populatePanel(model, studentId);
                return "students/detail :: applicationsPanel";
            }
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (DuplicateApplicationException | CourseNotFoundException | StudentNotFoundException ex) {
            if (htmxRequest != null) {
                model.addAttribute("errorMessage", ex.getMessage());
                populatePanel(model, studentId);
                return "students/detail :: applicationsPanel";
            }
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/students/" + studentId;
    }

    // ── Status change (from student detail) ──────────────────────────────────────

    @PostMapping("/applications/{id}/status")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String updateStatus(
            @PathVariable UUID id,
            @RequestParam ApplicationStatus newStatus,
            @RequestParam UUID studentId,
            @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
            Model model, RedirectAttributes redirectAttributes) {

        try {
            ApplicationResponse application = applicationService.updateStatus(id, newStatus);
            String msg = "Application moved to " + application.getStatus() + ".";
            if (htmxRequest != null) {
                model.addAttribute("successMessage", msg);
                populatePanel(model, studentId);
                return "students/detail :: applicationsPanel";
            }
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (InvalidApplicationStatusTransitionException | ApplicationNotFoundException ex) {
            if (htmxRequest != null) {
                model.addAttribute("errorMessage", ex.getMessage());
                populatePanel(model, studentId);
                return "students/detail :: applicationsPanel";
            }
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/students/" + studentId;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Loads the data the applicationsPanel fragment needs for the given student. */
    private void populatePanel(Model model, UUID studentId) {
        model.addAttribute("studentApplications", applicationService.listForStudent(studentId));
        model.addAttribute("applicableCourses", courseService.listActive());
        model.addAttribute("panelStudentId", studentId);
        model.addAttribute("applicationStatuses", ApplicationStatus.values());
        // Marks this as a standalone HTMX fragment render (not a full page). The panel
        // surfaces its own flash toasts only here; on a full page load the layout-level
        // toastMarkers already handle them, so the flag prevents duplicate toasts.
        model.addAttribute("isPanelFragment", true);
    }

    private void addNavAttributes(Model model, Authentication auth) {
        EduFlowUserDetails principal = (EduFlowUserDetails) auth.getPrincipal();
        model.addAttribute("fullName", principal.getFullName().trim());
        model.addAttribute("email", principal.getUsername());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
