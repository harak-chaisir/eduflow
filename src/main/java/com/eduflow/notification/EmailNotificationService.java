package com.eduflow.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends notification emails (PRD §13). The {@link JavaMailSender} is optional — when no
 * {@code spring.mail.*} is configured (e.g. local dev) the bean is absent and the email is
 * logged instead of sent, so notifications never block on mail infrastructure.
 *
 * <p>Dispatch is {@code @Async} so a slow/unavailable SMTP server never delays the
 * triggering request.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Async
    public void send(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("[email stub] to={} subject='{}' (no mail sender configured)", toEmail, subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.debug("Notification email sent to {}", toEmail);
        } catch (RuntimeException ex) {
            log.warn("Failed to send notification email to {}: {}", toEmail, ex.getMessage());
        }
    }
}
