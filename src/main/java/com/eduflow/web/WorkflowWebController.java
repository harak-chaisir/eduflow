package com.eduflow.web;

import com.eduflow.document.DocumentType;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.workflow.StageType;
import com.eduflow.workflow.TransitionType;
import com.eduflow.workflow.WorkflowArchivedException;
import com.eduflow.workflow.WorkflowTemplate;
import com.eduflow.workflow.WorkflowTemplateService;
import com.eduflow.workflow.DuplicateWorkflowNameException;
import com.eduflow.workflow.InvalidWorkflowGraphException;
import com.eduflow.workflow.WorkflowTemplateNotFoundException;
import com.eduflow.workflow.dto.WorkflowSearchCriteria;
import com.eduflow.workflow.dto.WorkflowStageRequest;
import com.eduflow.workflow.dto.WorkflowStageResponse;
import com.eduflow.workflow.dto.WorkflowTemplateRequest;
import com.eduflow.workflow.dto.WorkflowTemplateResponse;
import com.eduflow.workflow.dto.WorkflowTransitionRequest;
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

import java.util.List;
import java.util.UUID;

/**
 * Thymeleaf web controller for the Workflow Management builder (PRD §5–§9).
 *
 * <p>Pages live under {@code /workflows/**} and are restricted to tenant admins. Data
 * access goes through {@link WorkflowTemplateService}; the tenant is resolved from the
 * security context inside the service. The list view uses HTMX live search; builder
 * mutations use plain POST + redirect with flash messages.</p>
 */
@Slf4j
@Controller
@RequestMapping("/workflows")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
public class WorkflowWebController {

    /** Roles a tenant admin may assign as a stage owner (PRD §11). */
    private static final List<String> ASSIGNABLE_ROLES = List.of(
            "ROLE_COUNSELOR", "ROLE_DOC_OFFICER", "ROLE_VISA_OFFICER", "ROLE_TENANT_ADMIN");

    private final WorkflowTemplateService workflowService;

    // ── List / Search ────────────────────────────────────────────────────────

    @GetMapping
    public String list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model, Authentication auth) {

        addNavAttributes(model, auth);
        populateSearchModel(model, name, archived, page, size);
        return "workflows/list";
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        populateSearchModel(model, name, archived, page, size);
        return "workflows/list :: workflowResults";
    }

    private void populateSearchModel(Model model, String name, Boolean archived, int page, int size) {
        WorkflowSearchCriteria criteria = WorkflowSearchCriteria.builder()
                .name(name).archived(archived).build();
        Page<WorkflowTemplateResponse> workflowsPage = workflowService
                .searchAsResponses(criteria, PageRequest.of(page, size, Sort.by("name").ascending()));
        model.addAttribute("workflowsPage", workflowsPage);
        model.addAttribute("searchName", name);
        model.addAttribute("searchArchived", archived);
        model.addAttribute("currentSize", size);
    }

    // ── Create / Edit properties ───────────────────────────────────────────────

    @GetMapping("/new")
    public String showCreateForm(Model model, Authentication auth) {
        addNavAttributes(model, auth);
        model.addAttribute("workflowForm", new WorkflowTemplateRequest());
        model.addAttribute("isEdit", false);
        return "workflows/form";
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute("workflowForm") WorkflowTemplateRequest form,
            BindingResult result, Model model, Authentication auth,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            addNavAttributes(model, auth);
            model.addAttribute("isEdit", false);
            return "workflows/form";
        }
        try {
            WorkflowTemplate created = workflowService.create(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Workflow '" + created.getName() + "' created. Add stages and transitions below.");
            return "redirect:/workflows/" + created.getId();
        } catch (DuplicateWorkflowNameException ex) {
            addNavAttributes(model, auth);
            model.addAttribute("isEdit", false);
            model.addAttribute("errorMessage", ex.getMessage());
            return "workflows/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable UUID id, Model model, Authentication auth,
                               RedirectAttributes redirectAttributes) {
        addNavAttributes(model, auth);
        try {
            WorkflowTemplate t = workflowService.getWithGraph(id);
            WorkflowTemplateRequest form = WorkflowTemplateRequest.builder()
                    .name(t.getName()).description(t.getDescription())
                    .country(t.getCountry()).active(t.isActive()).build();
            model.addAttribute("workflowForm", form);
            model.addAttribute("isEdit", true);
            model.addAttribute("workflowId", id);
            return "workflows/form";
        } catch (WorkflowTemplateNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/workflows";
        }
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("workflowForm") WorkflowTemplateRequest form,
                         BindingResult result, Model model, Authentication auth,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addNavAttributes(model, auth);
            model.addAttribute("isEdit", true);
            model.addAttribute("workflowId", id);
            return "workflows/form";
        }
        try {
            workflowService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Workflow updated.");
        } catch (DuplicateWorkflowNameException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workflows/" + id;
    }

    // ── Builder / Detail ─────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String builder(@PathVariable UUID id, Model model, Authentication auth,
                          RedirectAttributes redirectAttributes) {
        addNavAttributes(model, auth);
        try {
            WorkflowTemplate t = workflowService.getWithGraph(id);
            model.addAttribute("workflow", WorkflowTemplateResponse.withGraph(t));
            addBuilderReferenceData(model);
            model.addAttribute("newStage", new WorkflowStageRequest());
            return "workflows/builder";
        } catch (WorkflowTemplateNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/workflows";
        }
    }

    // ── Lifecycle actions ────────────────────────────────────────────────────

    @PostMapping("/{id}/clone")
    public String clone(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        WorkflowTemplate copy = workflowService.clone(id);
        redirectAttributes.addFlashAttribute("successMessage",
                "Workflow cloned as version " + copy.getVersion() + ".");
        return "redirect:/workflows/" + copy.getId();
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        workflowService.deactivate(id);
        redirectAttributes.addFlashAttribute("successMessage", "Workflow deactivated.");
        return "redirect:/workflows/" + id;
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        workflowService.archive(id);
        redirectAttributes.addFlashAttribute("successMessage", "Workflow archived.");
        return "redirect:/workflows/" + id;
    }

    @PostMapping("/{id}/default")
    public String setDefault(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            workflowService.setDefault(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "This workflow is now the default for new students.");
        } catch (WorkflowArchivedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workflows/" + id;
    }

    @PostMapping("/{id}/validate")
    public String validate(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            workflowService.validateGraph(id);
            redirectAttributes.addFlashAttribute("successMessage", "Workflow graph is valid.");
        } catch (InvalidWorkflowGraphException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workflows/" + id;
    }

    // ── Stage configuration ──────────────────────────────────────────────────

    @PostMapping("/{id}/stages")
    public String addStage(@PathVariable UUID id,
                           @Valid @ModelAttribute("newStage") WorkflowStageRequest form,
                           BindingResult result, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the stage form.");
            return "redirect:/workflows/" + id;
        }
        try {
            workflowService.addStage(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Stage '" + form.getName() + "' added.");
        } catch (InvalidWorkflowGraphException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workflows/" + id;
    }

    @GetMapping("/{id}/stages/{stageId}/edit")
    public String showStageForm(@PathVariable UUID id, @PathVariable UUID stageId,
                                Model model, Authentication auth,
                                RedirectAttributes redirectAttributes) {
        addNavAttributes(model, auth);
        WorkflowTemplate t = workflowService.getWithGraph(id);
        WorkflowStageResponse stage = t.getStages().stream()
                .filter(s -> s.getId().equals(stageId))
                .findFirst().map(WorkflowStageResponse::from).orElse(null);
        if (stage == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Stage not found.");
            return "redirect:/workflows/" + id;
        }
        WorkflowStageRequest form = WorkflowStageRequest.builder()
                .name(stage.getName()).code(stage.getCode()).displayOrder(stage.getDisplayOrder())
                .description(stage.getDescription()).color(stage.getColor()).active(stage.isActive())
                .slaDays(stage.getSlaDays()).stageType(stage.getStageType())
                .ownerRole(stage.getOwnerRole()).requiredDocuments(stage.getRequiredDocuments()).build();
        model.addAttribute("stageForm", form);
        model.addAttribute("workflow", WorkflowTemplateResponse.from(t));
        model.addAttribute("stageId", stageId);
        addBuilderReferenceData(model);
        return "workflows/stage-form";
    }

    @PostMapping("/{id}/stages/{stageId}/edit")
    public String updateStage(@PathVariable UUID id, @PathVariable UUID stageId,
                              @Valid @ModelAttribute("stageForm") WorkflowStageRequest form,
                              BindingResult result, Model model, Authentication auth,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addNavAttributes(model, auth);
            model.addAttribute("workflow", WorkflowTemplateResponse.from(workflowService.getWithGraph(id)));
            model.addAttribute("stageId", stageId);
            addBuilderReferenceData(model);
            return "workflows/stage-form";
        }
        workflowService.updateStage(stageId, form);
        redirectAttributes.addFlashAttribute("successMessage", "Stage updated.");
        return "redirect:/workflows/" + id;
    }

    @PostMapping("/{id}/stages/{stageId}/delete")
    public String deleteStage(@PathVariable UUID id, @PathVariable UUID stageId,
                              RedirectAttributes redirectAttributes) {
        workflowService.deleteStage(stageId);
        redirectAttributes.addFlashAttribute("successMessage", "Stage removed.");
        return "redirect:/workflows/" + id;
    }

    // ── Transition configuration ─────────────────────────────────────────────

    @PostMapping("/{id}/transitions")
    public String addTransition(@PathVariable UUID id,
                                @Valid @ModelAttribute WorkflowTransitionRequest form,
                                BindingResult result, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Select both a source and a target stage.");
            return "redirect:/workflows/" + id;
        }
        try {
            workflowService.addTransition(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Transition added.");
        } catch (InvalidWorkflowGraphException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workflows/" + id;
    }

    @PostMapping("/{id}/transitions/{transitionId}/delete")
    public String deleteTransition(@PathVariable UUID id, @PathVariable UUID transitionId,
                                   RedirectAttributes redirectAttributes) {
        workflowService.deleteTransition(transitionId);
        redirectAttributes.addFlashAttribute("successMessage", "Transition removed.");
        return "redirect:/workflows/" + id;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void addBuilderReferenceData(Model model) {
        model.addAttribute("stageTypes", StageType.values());
        model.addAttribute("transitionTypes", TransitionType.values());
        model.addAttribute("documentTypes", DocumentType.values());
        model.addAttribute("assignableRoles", ASSIGNABLE_ROLES);
    }

    private void addNavAttributes(Model model, Authentication auth) {
        EduFlowUserDetails principal = (EduFlowUserDetails) auth.getPrincipal();
        model.addAttribute("fullName", principal.getFullName().trim());
        model.addAttribute("email", principal.getUsername());
    }
}
