package com.eduflow.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Notification}. All finders are tenant-scoped.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndTenantId(UUID id, UUID tenantId);

    /** In-app feed for a recipient, newest first. */
    List<Notification> findByTenantIdAndRecipientUserIdAndChannelOrderByCreatedAtDesc(
            UUID tenantId, UUID recipientUserId, String channel);

    long countByTenantIdAndRecipientUserIdAndChannelAndReadAtIsNull(
            UUID tenantId, UUID recipientUserId, String channel);
}
