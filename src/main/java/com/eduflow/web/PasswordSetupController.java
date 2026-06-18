package com.eduflow.web;

import com.eduflow.user.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Public set-password / password-reset flow (no authentication required).
 *
 * <p>Reached via the single-use link issued when a tenant admin is invited. Setting a
 * password activates the account ({@code PENDING_VERIFICATION → ACTIVE}) so the user can
 * sign in at {@code /login}.</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PasswordSetupController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final PasswordResetService passwordResetService;

    /** Renders the set-password form for a valid token, or an "invalid link" state. */
    @GetMapping("/set-password")
    public String showForm(@RequestParam(required = false) String token, Model model) {
        Optional<String> email = token == null ? Optional.empty()
                : passwordResetService.emailForValidToken(token);
        if (email.isEmpty()) {
            model.addAttribute("invalid", true);
            return "set-password";
        }
        model.addAttribute("token", token);
        model.addAttribute("email", email.get());
        return "set-password";
    }

    /** Validates the form and consumes the token, then redirects to the login page. */
    @PostMapping("/set-password")
    public String submit(@RequestParam String token,
                         @RequestParam String password,
                         @RequestParam String confirmPassword,
                         Model model) {

        if (passwordResetService.emailForValidToken(token).isEmpty()) {
            model.addAttribute("invalid", true);
            return "set-password";
        }

        String error = validate(password, confirmPassword);
        if (error != null) {
            model.addAttribute("token", token);
            model.addAttribute("email", passwordResetService.emailForValidToken(token).orElse(null));
            model.addAttribute("errorMessage", error);
            return "set-password";
        }

        boolean ok = passwordResetService.resetPassword(token, password);
        if (!ok) {
            model.addAttribute("invalid", true);
            return "set-password";
        }
        return "redirect:/login?reset";
    }

    private String validate(String password, String confirmPassword) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (!password.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        return null;
    }
}
