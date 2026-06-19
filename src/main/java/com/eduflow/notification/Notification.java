package com.eduflow.notification;

import com.eduflow.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted notification — one row per recipient per channel (PRD §7.8). In-app rows
 * drive the bell/feed; email rows record dispatch for audit/retry.
 *
 * <p>Tenant and recipient are stored as plain UUID columns (queried, not navigated) to
 * keep the feed query lean. ⚠️ Every query MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    /** Delivery channel: {@code IN_APP}, {@code EMAIL}, … (see tenant channel settings). */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "link", length = 500)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.SENT;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
