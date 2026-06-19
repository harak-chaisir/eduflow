package com.eduflow.notification;

import com.eduflow.tenant.TenantSettings;
import com.eduflow.tenant.TenantSettingsRepository;
import com.eduflow.user.User;
import com.eduflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dispatches notifications across the channels a tenant has enabled (PRD §13, §7.8).
 *
 * <p>Always persists an in-app notification; additionally sends email when the tenant's
 * {@code default_notification_channels} include {@code EMAIL}. SMS/WhatsApp are recognised
 * for forward-compatibility (NFR-6) but not yet dispatched. Recipients can be resolved by
 * user id or by role within the tenant.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final String IN_APP = "IN_APP";
    static final String EMAIL = "EMAIL";

    private final NotificationRepository notificationRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final UserRepository userRepository;
    private final EmailNotificationService emailNotificationService;

    /** Notifies a single user, persisting in-app and optionally emailing per tenant settings. */
    @Transactional
    public void notifyUser(UUID tenantId, UUID userId, NotificationType type,
                           String title, String body, String link) {
        Set<String> channels = enabledChannels(tenantId);
        Instant now = Instant.now();

        // In-app is always recorded (drives the bell/feed).
        notificationRepository.save(Notification.builder()
                .tenantId(tenantId).recipientUserId(userId).type(type)
                .channel(IN_APP).title(title).body(body).link(link)
                .status(NotificationStatus.SENT).sentAt(now).build());

        if (channels.contains(EMAIL)) {
            Notification emailRecord = notificationRepository.save(Notification.builder()
                    .tenantId(tenantId).recipientUserId(userId).type(type)
                    .channel(EMAIL).title(title).body(body).link(link)
                    .status(NotificationStatus.SENT).sentAt(now).build());
            userRepository.findByIdAndTenantId(userId, tenantId)
                    .map(User::getEmail)
                    .ifPresent(email -> emailNotificationService.send(email, title, body));
            log.debug("Queued email notification {} to user {}", emailRecord.getId(), userId);
        }
    }

    // ── Read / feed ──────────────────────────────────────────────────────────

    /** In-app feed for a recipient (newest first). */
    @Transactional(readOnly = true)
    public List<com.eduflow.notification.dto.NotificationResponse> listInApp(UUID tenantId, UUID userId) {
        return notificationRepository
                .findByTenantIdAndRecipientUserIdAndChannelOrderByCreatedAtDesc(tenantId, userId, IN_APP)
                .stream().map(com.eduflow.notification.dto.NotificationResponse::from).toList();
    }

    /** Count of unread in-app notifications, for the topbar badge. */
    @Transactional(readOnly = true)
    public long unreadCount(UUID tenantId, UUID userId) {
        return notificationRepository
                .countByTenantIdAndRecipientUserIdAndChannelAndReadAtIsNull(tenantId, userId, IN_APP);
    }

    /** Marks one in-app notification read (scoped to recipient + tenant). */
    @Transactional
    public void markRead(UUID tenantId, UUID userId, UUID notificationId) {
        notificationRepository.findByIdAndTenantId(notificationId, tenantId)
                .filter(n -> n.getRecipientUserId().equals(userId))
                .filter(n -> n.getReadAt() == null)
                .ifPresent(n -> {
                    n.setReadAt(Instant.now());
                    notificationRepository.save(n);
                });
    }

    /** Marks all of the user's unread in-app notifications read. */
    @Transactional
    public void markAllRead(UUID tenantId, UUID userId) {
        notificationRepository
                .findByTenantIdAndRecipientUserIdAndChannelOrderByCreatedAtDesc(tenantId, userId, IN_APP)
                .stream().filter(n -> n.getReadAt() == null)
                .forEach(n -> {
                    n.setReadAt(Instant.now());
                    notificationRepository.save(n);
                });
    }

    /** Notifies every active user holding {@code roleName} in the tenant. */
    @Transactional
    public void notifyRole(UUID tenantId, String roleName, NotificationType type,
                           String title, String body, String link) {
        if (roleName == null || roleName.isBlank()) {
            return;
        }
        List<User> recipients = userRepository.findActiveByTenantIdAndRoleName(tenantId, roleName);
        for (User u : recipients) {
            notifyUser(tenantId, u.getId(), type, title, body, link);
        }
    }

    private Set<String> enabledChannels(UUID tenantId) {
        return tenantSettingsRepository.findByTenantId(tenantId)
                .map(TenantSettings::getDefaultNotificationChannels)
                .map(csv -> Arrays.stream(csv.split(","))
                        .map(s -> s.trim().toUpperCase())
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet()))
                .orElse(Set.of(EMAIL));     // platform default mirrors the column default
    }
}
