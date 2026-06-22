package com.eduflow.web;

import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.task.InvalidTaskStatusTransitionException;
import com.eduflow.task.TaskNotFoundException;
import com.eduflow.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Thymeleaf controller for the "My Tasks" view (PRD §7.7). The list shows the open tasks
 * owned by the current user or their roles; start/complete actions support both plain POST
 * (redirect) and HTMX partial refresh (returning the {@code tasksTab} fragment).
 */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TaskWebController {

    private final TaskService taskService;

    @GetMapping
    public String myTasks(Model model, Authentication auth) {
        addNavAttributes(model, auth);
        model.addAttribute("tasks", taskService.listMyTasks());
        return "tasks/list";
    }

    @PostMapping("/{id}/start")
    public String start(@PathVariable UUID id,
                        @RequestParam(required = false) UUID studentId,
                        @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        try {
            taskService.start(id);
            if (htmxRequest != null && studentId != null) {
                model.addAttribute("successMessage", "Task started.");
                model.addAttribute("studentTasks", taskService.listForStudent(studentId));
                return "tasks/student-tasks :: tasksTab";
            }
            redirectAttributes.addFlashAttribute("successMessage", "Task started.");
        } catch (TaskNotFoundException | InvalidTaskStatusTransitionException ex) {
            if (htmxRequest != null && studentId != null) {
                model.addAttribute("errorMessage", ex.getMessage());
                model.addAttribute("studentTasks", taskService.listForStudent(studentId));
                return "tasks/student-tasks :: tasksTab";
            }
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/tasks";
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable UUID id,
                           @RequestParam(required = false) UUID studentId,
                           @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        try {
            taskService.complete(id);
            if (htmxRequest != null && studentId != null) {
                model.addAttribute("successMessage", "Task completed.");
                model.addAttribute("studentTasks", taskService.listForStudent(studentId));
                return "tasks/student-tasks :: tasksTab";
            }
            redirectAttributes.addFlashAttribute("successMessage", "Task completed.");
        } catch (TaskNotFoundException | InvalidTaskStatusTransitionException ex) {
            if (htmxRequest != null && studentId != null) {
                model.addAttribute("errorMessage", ex.getMessage());
                model.addAttribute("studentTasks", taskService.listForStudent(studentId));
                return "tasks/student-tasks :: tasksTab";
            }
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/tasks";
    }

    private void addNavAttributes(Model model, Authentication auth) {
        EduFlowUserDetails principal = (EduFlowUserDetails) auth.getPrincipal();
        model.addAttribute("fullName", principal.getFullName().trim());
        model.addAttribute("email", principal.getUsername());
    }
}
