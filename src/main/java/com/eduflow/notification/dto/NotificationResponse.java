package com.eduflow.notification.dto;

import com.eduflow.notification.Notification;
import com.eduflow.notification.NotificationType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/** Immutable view of an in-app {@link Notification}. */
@Value
@Builder
public class NotificationResponse {

    UUID id;
    NotificationType type;
    String title;
    String body;
    String link;
    boolean read;
    Instant createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .link(n.getLink())
                .read(n.getReadAt() != null)
                .createdAt(n.getCreatedAt())
                .build();
    }
}
