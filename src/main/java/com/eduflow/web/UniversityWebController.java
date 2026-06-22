package com.eduflow.web;

import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.university.CourseLevel;
import com.eduflow.university.CourseNotFoundException;
import com.eduflow.university.CourseService;
import com.eduflow.university.UniversityNotFoundException;
import com.eduflow.university.UniversityService;
import com.eduflow.university.dto.CourseRequest;
import com.eduflow.university.dto.CourseResponse;
import com.eduflow.university.dto.UniversityRequest;
import com.eduflow.university.dto.UniversityResponse;
import com.eduflow.university.dto.UniversitySearchCriteria;
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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thymeleaf web controller for the University / Course catalog at {@code /universities/**}.
 *
 * <p>Follows the {@link StudentWebController} HTMX pattern: live-search returns the
 * {@code universityResults} fragment; full navigations return the whole view.</p>
 */
@Slf4j
@Controller
@RequestMapping("/universities")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN','DOC_OFFICER','VISA_OFFICER')")
public class UniversityWebController {

    private final UniversityService universityService;
    private final CourseService courseService;

    // ── List / Search ────────────────────────────────────────────────────────

    @GetMapping
    public String list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model, Authentication auth) {

        addNavAttributes(model, auth);
        populateSearchModel(model, q, country, page, size);
        return "universities/list";
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        populateSearchModel(model, q, country, page, size);
        return "universities/list :: universityResults";
    }

    private void populateSearchModel(Model model, String q, String country, int page, int size) {
        UniversitySearchCriteria criteria = UniversitySearchCriteria.builder()
                .q(q).country(country).build();
        Page<UniversityResponse> universitiesPage = universityService.search(
                criteria, PageRequest.of(page, size, Sort.by("name").ascending()));
        model.addAttribute("universitiesPage", universitiesPage);
        model.addAttribute("searchQ", q);
        model.addAttribute("searchCountry", country);
        model.addAttribute("currentSize", size);
    }

    // ── Create university ──────────────────────────────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String showCreateForm(Model model, Authentication auth) {
        addNavAttributes(model, auth);
        model.addAttribute("universityForm", new UniversityFormData());
        model.addAttribute("isEdit", false);
        return "universities/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String create(
            @Valid @ModelAttribute("universityForm") UniversityFormData form,
            BindingResult result, Model model, Authentication auth,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            addNavAttributes(model, auth);
            model.addAttribute("isEdit", false);
            return "universities/form";
        }
        UniversityResponse saved = universityService.create(toRequest(form));
        redirectAttributes.addFlashAttribute("successMessage",
                "University '" + saved.getName() + "' was created.");
        return "redirect:/universities/" + saved.getId();
    }

    // ── Detail ─────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model, Authentication auth,
                         RedirectAttributes redirectAttributes) {
        addNavAttributes(model, auth);
        try {
            model.addAttribute("university", universityService.get(id));
            model.addAttribute("courses", courseService.listForUniversity(id));
            model.addAttribute("courseForm", new CourseFormData());
            model.addAttribute("levels", CourseLevel.values());
            return "universities/detail";
        } catch (UniversityNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/universities";
        }
    }

    // ── Edit university ────────────────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String showEditForm(@PathVariable UUID id, Model model, Authentication auth,
                               RedirectAttributes redirectAttributes) {
        addNavAttributes(model, auth);
        try {
            UniversityResponse u = universityService.get(id);
            model.addAttribute("universityForm", toFormData(u));
            model.addAttribute("isEdit", true);
            model.addAttribute("universityId", id);
            model.addAttribute("universityName", u.getName());
            return "universities/form";
        } catch (UniversityNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/universities";
        }
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("universityForm") UniversityFormData form,
            BindingResult result, Model model, Authentication auth,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            addNavAttributes(model, auth);
            model.addAttribute("isEdit", true);
            model.addAttribute("universityId", id);
            return "universities/form";
        }
        try {
            UniversityResponse saved = universityService.update(id, toRequest(form));
            redirectAttributes.addFlashAttribute("successMessage",
                    "University '" + saved.getName() + "' was updated.");
            return "redirect:/universities/" + id;
        } catch (UniversityNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/universities";
        }
    }

    // ── Courses ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/courses")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String createCourse(
            @PathVariable UUID id,
            @Valid @ModelAttribute("courseForm") CourseFormData form,
            BindingResult result, Model model, Authentication auth,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            addNavAttributes(model, auth);
            model.addAttribute("university", universityService.get(id));
            model.addAttribute("courses", courseService.listForUniversity(id));
            model.addAttribute("levels", CourseLevel.values());
            model.addAttribute("showCourseForm", true);
            return "universities/detail";
        }
        try {
            CourseResponse course = courseService.create(id, toCourseRequest(form));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Course '" + course.getName() + "' was added.");
        } catch (UniversityNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/universities/" + id;
    }

    @GetMapping("/{id}/courses/{courseId}/edit")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String showCourseEditForm(@PathVariable UUID id, @PathVariable UUID courseId,
                                     Model model, Authentication auth,
                                     RedirectAttributes redirectAttributes) {
        addNavAttributes(model, auth);
        try {
            model.addAttribute("university", universityService.get(id));
            model.addAttribute("courseForm", toCourseFormData(courseService.get(courseId)));
            model.addAttribute("levels", CourseLevel.values());
            model.addAttribute("courseId", courseId);
            model.addAttribute("universityId", id);
            return "universities/course-form";
        } catch (UniversityNotFoundException | CourseNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/universities/" + id;
        }
    }

    @PostMapping("/{id}/courses/{courseId}/edit")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String updateCourse(
            @PathVariable UUID id, @PathVariable UUID courseId,
            @Valid @ModelAttribute("courseForm") CourseFormData form,
            BindingResult result, Model model, Authentication auth,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            addNavAttributes(model, auth);
            model.addAttribute("university", universityService.get(id));
            model.addAttribute("levels", CourseLevel.values());
            model.addAttribute("courseId", courseId);
            model.addAttribute("universityId", id);
            return "universities/course-form";
        }
        try {
            CourseResponse course = courseService.update(courseId, toCourseRequest(form));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Course '" + course.getName() + "' was updated.");
        } catch (CourseNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/universities/" + id;
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private void addNavAttributes(Model model, Authentication auth) {
        EduFlowUserDetails principal = (EduFlowUserDetails) auth.getPrincipal();
        model.addAttribute("fullName", principal.getFullName().trim());
        model.addAttribute("email", principal.getUsername());
    }

    private UniversityRequest toRequest(UniversityFormData form) {
        return UniversityRequest.builder()
                .name(form.getName())
                .country(form.getCountry())
                .city(blankToNull(form.getCity()))
                .website(blankToNull(form.getWebsite()))
                .code(blankToNull(form.getCode()))
                .active(form.isActive())
                .build();
    }

    private UniversityFormData toFormData(UniversityResponse u) {
        UniversityFormData form = new UniversityFormData();
        form.setName(u.getName());
        form.setCountry(u.getCountry());
        form.setCity(u.getCity());
        form.setWebsite(u.getWebsite());
        form.setCode(u.getCode());
        form.setActive(u.isActive());
        return form;
    }

    private CourseRequest toCourseRequest(CourseFormData form) {
        return CourseRequest.builder()
                .name(form.getName())
                .level(CourseLevel.valueOf(form.getLevel()))
                .intakeMonth(parseInt(form.getIntakeMonth()))
                .intakeYear(parseInt(form.getIntakeYear()))
                .tuitionFee(parseDecimal(form.getTuitionFee()))
                .entryRequirements(blankToNull(form.getEntryRequirements()))
                .active(form.isActive())
                .build();
    }

    private CourseFormData toCourseFormData(CourseResponse c) {
        CourseFormData form = new CourseFormData();
        form.setName(c.getName());
        form.setLevel(c.getLevel() != null ? c.getLevel().name() : null);
        form.setIntakeMonth(c.getIntakeMonth() != null ? c.getIntakeMonth().toString() : null);
        form.setIntakeYear(c.getIntakeYear() != null ? c.getIntakeYear().toString() : null);
        form.setTuitionFee(c.getTuitionFee() != null ? c.getTuitionFee().toPlainString() : null);
        form.setEntryRequirements(c.getEntryRequirements());
        form.setActive(c.isActive());
        return form;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
