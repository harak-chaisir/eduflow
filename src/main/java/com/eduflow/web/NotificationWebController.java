package com.eduflow.web;

import com.eduflow.notification.NotificationService;
import com.eduflow.security.EduFlowUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * Thymeleaf controller for the in-app notification feed (PRD §13).
 */
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationWebController {

    private final NotificationService notificationService;

    @GetMapping
    public String list(Model model, Authentication auth) {
        EduFlowUserDetails p = (EduFlowUserDetails) auth.getPrincipal();
        model.addAttribute("fullName", p.getFullName().trim());
        model.addAttribute("email", p.getUsername());
        model.addAttribute("notifications", notificationService.listInApp(p.getTenantId(), p.getUserId()));
        return "notifications/list";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable UUID id, Authentication auth) {
        EduFlowUserDetails p = (EduFlowUserDetails) auth.getPrincipal();
        notificationService.markRead(p.getTenantId(), p.getUserId(), id);
        return "redirect:/notifications";
    }

    @PostMapping("/read-all")
    public String markAllRead(Authentication auth) {
        EduFlowUserDetails p = (EduFlowUserDetails) auth.getPrincipal();
        notificationService.markAllRead(p.getTenantId(), p.getUserId());
        return "redirect:/notifications";
    }
}
