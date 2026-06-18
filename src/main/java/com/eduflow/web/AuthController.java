package com.eduflow.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Handles the login page and logout confirmation.
 */
@Controller
public class AuthController {

    /**
     * Renders the login page.
     *
     * @param error   present when Spring Security redirects after a failed login attempt
     * @param logout  present when the user has just signed out
     * @param model   Thymeleaf model
     * @return the {@code login} Thymeleaf template
     */
    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String reset,
            Model model) {

        if (error != null) {
            model.addAttribute("errorMessage", "Invalid email or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been signed out successfully.");
        }
        if (reset != null) {
            model.addAttribute("logoutMessage",
                    "Your password has been set. You can now sign in.");
        }
        return "login";
    }
}

