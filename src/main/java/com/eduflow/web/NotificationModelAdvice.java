package com.eduflow.web;

import com.eduflow.notification.NotificationService;
import com.eduflow.security.EduFlowUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the authenticated user's unread in-app notification count to every rendered
 * view, so the topbar bell badge (in {@code fragments/layout}) is populated everywhere.
 * Restricted to {@code @Controller}s (server-rendered pages), not REST controllers.
 */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
@RequiredArgsConstructor
public class NotificationModelAdvice {

    private final NotificationService notificationService;

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof EduFlowUserDetails p)) {
            return 0;
        }
        try {
            return notificationService.unreadCount(p.getTenantId(), p.getUserId());
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}
