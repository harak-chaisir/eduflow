package com.eduflow.web;

import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;



/**
 * Handles the main dashboard / landing page shown after a successful login.
 * The dashboard adapts its content based on the authenticated user's roles.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final StudentService studentService;

    /**
     * Redirects the root URL to the dashboard.
     */
    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    /**
     * Renders the post-login dashboard / landing page.
     *
     * @param authentication the current Spring Security authentication
     * @param model          Thymeleaf model
     * @return the {@code dashboard} Thymeleaf template
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        EduFlowUserDetails principal = (EduFlowUserDetails) authentication.getPrincipal();

        String fullName = principal.getFullName();
        model.addAttribute("fullName", fullName != null ? fullName.trim() : "");
        model.addAttribute("email", principal.getUsername());
        model.addAttribute("tenantId", principal.getTenantId());
        model.addAttribute("roles", authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        model.addAttribute("tenantName", principal.getTenantName());
        model.addAttribute("statusCounts", studentService.countByStatus());
        model.addAttribute("totalStudents", studentService.countAll());

        log.debug("Dashboard accessed by {} (tenant={})", principal.getUsername(), principal.getTenantId());
        return "dashboard";
    }
}



