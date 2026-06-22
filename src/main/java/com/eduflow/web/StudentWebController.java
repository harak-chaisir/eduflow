package com.eduflow.web;

import com.eduflow.application.ApplicationService;
import com.eduflow.application.ApplicationStatus;
import com.eduflow.document.DocumentService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.*;
import com.eduflow.student.dto.*;
import com.eduflow.task.TaskService;
import com.eduflow.university.CourseService;
import com.eduflow.workflow.InvalidWorkflowTransitionException;
import com.eduflow.workflow.RequiredDocumentsMissingException;
import com.eduflow.workflow.StudentWorkflowService;
import com.eduflow.workflow.WorkflowArchivedException;
import com.eduflow.workflow.WorkflowTemplate;
import com.eduflow.workflow.WorkflowTemplateService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Thymeleaf web controller for the Student module.
 *
 * <p>Handles page rendering at {@code /students/**}. All data access goes through
 * {@link StudentService}; the tenant is resolved from the security context inside the service.</p>
 */
@Slf4j
@Controller
@RequestMapping("/students")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN','DOC_OFFICER','VISA_OFFICER')")
public class StudentWebController {

    private final StudentService studentService;
    private final StudentWorkflowService studentWorkflowService;
    private final WorkflowTemplateService workflowTemplateService;
    private final ApplicationService applicationService;
    private final CourseService courseService;
    private final DocumentService documentService;
    private final TaskService taskService;

    // ── List / Search ────────────────────────────────────────────────────────

    /**
     * Renders the paginated student list with optional search filters.
     *
     * @param name    partial name match (first or last)
     * @param email   partial email match
     * @param status  exact status filter
     * @param page    zero-based page number
     * @param size    page size
     */
    @GetMapping
    public String listStudents(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model,
            Authentication auth) {

        addNavAttributes(model, auth);
        populateSearchModel(model, name, email, status, page, size);
        return "students/list";
    }

    /**
     * HTMX live-search endpoint — returns only the {@code studentResults} fragment.
     * Called by the debounced filter inputs and the pagination links in the list view.
     */
    @GetMapping("/search")
    public String searchStudents(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        populateSearchModel(model, name, email, status, page, size);
        // stampClass is added to the model so the fragment can reference it via ${stampClass}
        model.addAttribute("stampClass", java.util.Map.of(
                "LEAD", "stamp--empty", "QUALIFIED", "stamp--pending",
                "ACTIVE", "stamp--approved", "ENROLLED", "stamp--needs_revision",
                "INACTIVE", "stamp--rejected"));
        return "students/list :: studentResults";
    }

    /** Shared helper that puts search / pagination model attributes in place. */
    private void populateSearchModel(Model model, String name, String email,
                                     StudentStatus status, int page, int size) {
        StudentSearchCriteria criteria = StudentSearchCriteria.builder()
                .name(name).email(email).status(status).build();

        Page<StudentResponse> studentsPage = studentService.searchStudents(
                criteria, PageRequest.of(page, size, Sort.by("lastName").ascending()));

        model.addAttribute("studentsPage", studentsPage);
        model.addAttribute("searchName", name);
        model.addAttribute("searchEmail", email);
        model.addAttribute("searchStatus", status);
        model.addAttribute("statuses", StudentStatus.values());
        model.addAttribute("currentSize", size);
    }

    // ── Create ───────────────────────────────────────────────────────────────

    /** Renders the blank student registration form. */
    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String showCreateForm(Model model, Authentication auth) {
        addNavAttributes(model, auth);
        model.addAttribute("studentForm", new StudentFormData());
        addFormReferenceData(model, false);
        return "students/form";
    }

    /** Handles student registration form submission. */
    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String createStudent(
            @Valid @ModelAttribute("studentForm") StudentFormData form,
            BindingResult result,
            Model model,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            addNavAttributes(model, auth);
            addFormReferenceData(model, false);
            return "students/form";
        }

        try {
            StudentResponse student = studentService.registerStudent(toRegisterRequest(form));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Student '" + student.getFullName() + "' was registered successfully.");
            return "redirect:/students/" + student.getId();
        } catch (DuplicateStudentException ex) {
            addNavAttributes(model, auth);
            addFormReferenceData(model, false);
            model.addAttribute("errorMessage", ex.getMessage());
            return "students/form";
        }
    }

    // ── Detail ───────────────────────────────────────────────────────────────

    /** Renders the student detail / profile page. */
    @GetMapping("/{id}")
    public String viewStudent(
            @PathVariable UUID id,
            Model model,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        addNavAttributes(model, auth);
        try {
            model.addAttribute("student", studentService.getStudent(id));
            addWorkflowModel(model, id);
            addApplicationsModel(model, id);
            addDocumentReadiness(model, id);
            return "students/detail";
        } catch (StudentNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/students";
        }
    }

    // ── Workflow (assign / advance) ───────────────────────────────────────────

    /** Assigns a workflow template to the student. Supports HTMX partial refresh. */
    @PostMapping("/{id}/workflow/assign")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String assignWorkflow(@PathVariable UUID id,
                                 @RequestParam UUID templateId,
                                 @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        try {
            studentWorkflowService.assignWorkflow(id, templateId);
            if (htmxRequest != null) {
                model.addAttribute("student", studentService.getStudent(id));
                addWorkflowModel(model, id);
                model.addAttribute("successMessage", "Workflow assigned.");
                return "students/detail :: workflowCard";
            }
            redirectAttributes.addFlashAttribute("successMessage", "Workflow assigned.");
        } catch (WorkflowArchivedException ex) {
            if (htmxRequest != null) {
                model.addAttribute("student", studentService.getStudent(id));
                addWorkflowModel(model, id);
                model.addAttribute("errorMessage", ex.getMessage());
                return "students/detail :: workflowCard";
            }
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/students/" + id;
    }

    /** Advances the student's workflow instance to the chosen stage. Supports HTMX partial refresh. */
    @PostMapping("/{id}/workflow/{instanceId}/move")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN','DOC_OFFICER','VISA_OFFICER')")
    public String moveWorkflowStage(@PathVariable UUID id,
                                    @PathVariable UUID instanceId,
                                    @RequestParam UUID toStageId,
                                    @RequestParam(required = false) String notes,
                                    @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        try {
            studentWorkflowService.moveStage(instanceId, toStageId, notes);
            if (htmxRequest != null) {
                model.addAttribute("student", studentService.getStudent(id));
                addWorkflowModel(model, id);
                model.addAttribute("successMessage", "Workflow advanced.");
                return "students/detail :: workflowCard";
            }
            redirectAttributes.addFlashAttribute("successMessage", "Workflow advanced.");
        } catch (InvalidWorkflowTransitionException | RequiredDocumentsMissingException ex) {
            if (htmxRequest != null) {
                model.addAttribute("student", studentService.getStudent(id));
                addWorkflowModel(model, id);
                model.addAttribute("errorMessage", ex.getMessage());
                return "students/detail :: workflowCard";
            }
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/students/" + id;
    }

    // ── Tasks embed (HTMX lazy-load) ─────────────────────────────────────────

    /**
     * Returns the tasks panel fragment for the student detail page.
     * Lazy-loaded over HTMX the first time the Tasks tab is opened.
     */
    @GetMapping("/{id}/tasks/embed")
    public String tasksEmbed(@PathVariable UUID id, Model model) {
        model.addAttribute("studentTasks", taskService.listForStudent(id));
        return "tasks/student-tasks :: tasksTab";
    }

    /** Adds the student's workflow instance (if any) + assignable templates to the model. */
    private void addWorkflowModel(Model model, UUID studentId) {
        var workflow = studentWorkflowService.getForStudent(studentId);
        model.addAttribute("studentWorkflow", workflow.orElse(null));
        boolean hasActive = workflow.isPresent()
                && workflow.get().getStatus() == com.eduflow.workflow.InstanceStatus.ACTIVE;
        if (!hasActive) {
            model.addAttribute("assignableWorkflows", workflowTemplateService.listAssignable());
        }
    }

    /** Adds the student's applications + the active-course picker for the detail panel. */
    private void addApplicationsModel(Model model, UUID studentId) {
        model.addAttribute("studentApplications", applicationService.listForStudent(studentId));
        model.addAttribute("applicableCourses", courseService.listActive());
        model.addAttribute("panelStudentId", studentId);
        model.addAttribute("applicationStatuses", ApplicationStatus.values());
    }

    /**
     * Adds a compact document dossier to the model for the Overview readiness widget.
     * Non-critical — failures are swallowed so the rest of the detail page still renders.
     */
    private void addDocumentReadiness(Model model, UUID studentId) {
        try {
            model.addAttribute("dossier", documentService.getDossier(studentId));
        } catch (Exception ex) {
            log.warn("Could not load dossier for student {} — readiness widget will be hidden", studentId);
        }
    }

    // ── Edit ─────────────────────────────────────────────────────────────────

    /** Renders the edit form pre-populated with current student data. */
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String showEditForm(
            @PathVariable UUID id,
            Model model,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        addNavAttributes(model, auth);
        try {
            StudentResponse student = studentService.getStudent(id);
            model.addAttribute("studentForm", toFormData(student));
            addFormReferenceData(model, true);
            model.addAttribute("studentId", id);
            model.addAttribute("studentName", student.getFullName());
            return "students/form";
        } catch (StudentNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/students";
        }
    }

    /** Handles the edit form submission. */
    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String updateStudent(
            @PathVariable UUID id,
            @Valid @ModelAttribute("studentForm") StudentFormData form,
            BindingResult result,
            Model model,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            addNavAttributes(model, auth);
            addFormReferenceData(model, true);
            model.addAttribute("studentId", id);
            return "students/form";
        }

        try {
            StudentResponse student = studentService.updateStudent(id, toUpdateRequest(form));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Student '" + student.getFullName() + "' was updated successfully.");
            return "redirect:/students/" + id;
        } catch (DuplicateStudentException ex) {
            addNavAttributes(model, auth);
            addFormReferenceData(model, true);
            model.addAttribute("studentId", id);
            model.addAttribute("errorMessage", ex.getMessage());
            return "students/form";
        }
    }

    // ── Status transition ────────────────────────────────────────────────────

    /** Handles a status-change POST from the detail page. */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public String updateStatus(
            @PathVariable UUID id,
            @RequestParam StudentStatus newStatus,
            @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            StudentResponse student = studentService.updateStatus(id, newStatus);
            if (htmxRequest != null) {
                model.addAttribute("student", student);
                addStampClass(model);
                return "students/detail :: statusCard";
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Status changed to " + newStatus + " for " + student.getFullName() + ".");
        } catch (InvalidStudentStatusTransitionException | StudentNotFoundException ex) {
            if (htmxRequest != null) {
                try {
                    model.addAttribute("student", studentService.getStudent(id));
                } catch (StudentNotFoundException notFound) {
                    // Swallow — redirect will handle it.
                }
                model.addAttribute("errorMessage", ex.getMessage());
                addStampClass(model);
                return "students/detail :: statusCard";
            }
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/students/" + id;
    }

    // ── Soft-delete ───────────────────────────────────────────────────────────

    /** Soft-deletes (sets INACTIVE) the given student and redirects to the list. */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
    public String deleteStudent(
            @PathVariable UUID id,
            @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            studentService.deleteStudent(id);
            if (htmxRequest != null) {
                model.addAttribute("successMessage", "Student has been deactivated.");
                populateSearchModel(model, null, null, null, 0, 20);
                addStampClass(model);
                return "students/list :: studentResults";
            }
            redirectAttributes.addFlashAttribute("successMessage", "Student has been deactivated.");
        } catch (StudentNotFoundException ex) {
            if (htmxRequest != null) {
                model.addAttribute("errorMessage", ex.getMessage());
                populateSearchModel(model, null, null, null, 0, 20);
                addStampClass(model);
                return "students/list :: studentResults";
            }
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/students";
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void addNavAttributes(Model model, Authentication auth) {
        EduFlowUserDetails principal = (EduFlowUserDetails) auth.getPrincipal();
        model.addAttribute("fullName", principal.getFullName().trim());
        model.addAttribute("email", principal.getUsername());
    }

    /** Adds the status → CSS class map that both list and detail fragments need. */
    private void addStampClass(Model model) {
        model.addAttribute("stampClass", java.util.Map.of(
                "LEAD", "stamp--empty", "QUALIFIED", "stamp--pending",
                "ACTIVE", "stamp--approved", "ENROLLED", "stamp--needs_revision",
                "INACTIVE", "stamp--rejected"));
    }

    /** Reference data shared by the create and edit form views. */
    private void addFormReferenceData(Model model, boolean isEdit) {
        model.addAttribute("genders", Gender.values());
        model.addAttribute("counselors", studentService.listCounselors());
        model.addAttribute("isEdit", isEdit);
    }

    private RegisterStudentRequest toRegisterRequest(StudentFormData form) {
        return RegisterStudentRequest.builder()
                .firstName(form.getFirstName())
                .lastName(form.getLastName())
                .email(form.getEmail())
                .phone(form.getPhone())
                .dateOfBirth(parseDateOfBirth(form.getDateOfBirth()))
                .gender(parseGender(form.getGender()))
                .nationality(form.getNationality())
                .addressLine1(form.getAddressLine1())
                .addressLine2(form.getAddressLine2())
                .city(form.getCity())
                .stateProvince(form.getStateProvince())
                .country(form.getCountry())
                .postalCode(form.getPostalCode())
                .interestedCountries(parseList(form.getInterestedCountries()))
                .interestedCourses(parseList(form.getInterestedCourses()))
                .assignedCounselorId(form.getAssignedCounselorId())
                .build();
    }

    private UpdateStudentRequest toUpdateRequest(StudentFormData form) {
        return UpdateStudentRequest.builder()
                .firstName(form.getFirstName())
                .lastName(form.getLastName())
                .email(form.getEmail())
                .phone(form.getPhone())
                .dateOfBirth(parseDateOfBirth(form.getDateOfBirth()))
                .gender(parseGender(form.getGender()))
                .nationality(form.getNationality())
                .addressLine1(form.getAddressLine1())
                .addressLine2(form.getAddressLine2())
                .city(form.getCity())
                .stateProvince(form.getStateProvince())
                .country(form.getCountry())
                .postalCode(form.getPostalCode())
                .interestedCountries(parseList(form.getInterestedCountries()))
                .interestedCourses(parseList(form.getInterestedCourses()))
                .assignedCounselorId(form.getAssignedCounselorId())
                .build();
    }

    private StudentFormData toFormData(StudentResponse s) {
        StudentFormData form = new StudentFormData();
        form.setFirstName(s.getFirstName());
        form.setLastName(s.getLastName());
        form.setEmail(s.getEmail());
        form.setPhone(s.getPhone());
        if (s.getDateOfBirth() != null) {
            form.setDateOfBirth(s.getDateOfBirth().toString().substring(0, 10));
        }
        if (s.getGender() != null) {
            form.setGender(s.getGender().name());
        }
        form.setNationality(s.getNationality());
        form.setAddressLine1(s.getAddressLine1());
        form.setAddressLine2(s.getAddressLine2());
        form.setCity(s.getCity());
        form.setStateProvince(s.getStateProvince());
        form.setCountry(s.getCountry());
        form.setPostalCode(s.getPostalCode());
        if (s.getInterestedCountries() != null && !s.getInterestedCountries().isEmpty()) {
            form.setInterestedCountries(String.join(", ", s.getInterestedCountries()));
        }
        if (s.getInterestedCourses() != null && !s.getInterestedCourses().isEmpty()) {
            form.setInterestedCourses(String.join(", ", s.getInterestedCourses()));
        }
        form.setAssignedCounselorId(s.getAssignedCounselorId());
        return form;
    }

    private Instant parseDateOfBirth(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            log.warn("Could not parse date of birth: '{}'", dateStr);
            return null;
        }
    }

    private Gender parseGender(String genderStr) {
        if (genderStr == null || genderStr.isBlank()) return null;
        try {
            return Gender.valueOf(genderStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<String> parseList(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}

