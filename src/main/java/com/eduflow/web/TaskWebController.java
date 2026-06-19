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
 * owned by the current user or their roles; start/complete actions post and redirect.
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
    public String start(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        act(id, redirectAttributes, () -> taskService.start(id), "Task started.");
        return "redirect:/tasks";
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        act(id, redirectAttributes, () -> taskService.complete(id), "Task completed.");
        return "redirect:/tasks";
    }

    private void act(UUID id, RedirectAttributes ra, Runnable action, String success) {
        try {
            action.run();
            ra.addFlashAttribute("successMessage", success);
        } catch (TaskNotFoundException | InvalidTaskStatusTransitionException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
    }

    private void addNavAttributes(Model model, Authentication auth) {
        EduFlowUserDetails principal = (EduFlowUserDetails) auth.getPrincipal();
        model.addAttribute("fullName", principal.getFullName().trim());
        model.addAttribute("email", principal.getUsername());
    }
}
