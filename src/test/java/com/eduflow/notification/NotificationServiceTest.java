package com.eduflow.notification;

import com.eduflow.tenant.TenantSettings;
import com.eduflow.tenant.TenantSettingsRepository;
import com.eduflow.user.User;
import com.eduflow.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for {@link NotificationService} channel fan-out. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock NotificationRepository notificationRepository;
    @Mock TenantSettingsRepository tenantSettingsRepository;
    @Mock UserRepository userRepository;
    @Mock EmailNotificationService emailNotificationService;

    @InjectMocks NotificationService service;

    @Test
    void notifyUser_whenEmailEnabled_persistsBothChannelsAndSendsEmail() {
        TenantSettings settings = TenantSettings.builder()
                .tenantId(TENANT_ID).defaultNotificationChannels("EMAIL").build();
        when(tenantSettingsRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(settings));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        User user = User.builder().email("officer@x.com").build();
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.of(user));

        service.notifyUser(TENANT_ID, USER_ID, NotificationType.SLA_BREACHED, "T", "B", "/students/1");

        verify(notificationRepository, times(2)).save(any());   // IN_APP + EMAIL
        verify(emailNotificationService).send(eq("officer@x.com"), eq("T"), eq("B"));
    }

    @Test
    void notifyUser_whenOnlyInApp_persistsOnceAndDoesNotEmail() {
        TenantSettings settings = TenantSettings.builder()
                .tenantId(TENANT_ID).defaultNotificationChannels("IN_APP").build();
        when(tenantSettingsRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(settings));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.notifyUser(TENANT_ID, USER_ID, NotificationType.STAGE_ENTERED, "T", "B", null);

        verify(notificationRepository, times(1)).save(any());
        verify(emailNotificationService, never()).send(any(), any(), any());
    }
}
