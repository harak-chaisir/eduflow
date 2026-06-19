package com.eduflow.web;

import com.eduflow.report.WorkflowAnalyticsService;
import com.eduflow.security.EduFlowUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Tenant-admin workflow dashboard + analytics (PRD §15, §16).
 */
@Controller
@RequestMapping("/workflows/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
public class WorkflowDashboardController {

    private final WorkflowAnalyticsService analyticsService;

    @GetMapping
    public String dashboard(Model model, Authentication auth) {
        EduFlowUserDetails p = (EduFlowUserDetails) auth.getPrincipal();
        model.addAttribute("fullName", p.getFullName().trim());
        model.addAttribute("email", p.getUsername());
        model.addAttribute("metrics", analyticsService.dashboard());
        return "workflows/dashboard";
    }
}
