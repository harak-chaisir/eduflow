package com.eduflow.tenant;

import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.dto.ChangeTenantStatusRequest;
import com.eduflow.tenant.dto.CreateTenantRequest;
import com.eduflow.tenant.dto.TenantResponse;
import com.eduflow.tenant.event.TenantEvents;
import com.eduflow.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantService}. The Spring context is not loaded; the
 * {@link SecurityContextHolder} is populated with a super-admin principal so the
 * service can resolve the acting user.
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();

    @Mock TenantRepository tenantRepository;
    @Mock TenantSettingsRepository settingsRepository;
    @Mock AuditService auditService;
    @Mock ApplicationEventPublisher events;

    @InjectMocks TenantService tenantService;

    @BeforeEach
    void setUpSecurityContext() {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);
        User user = User.builder().tenant(tenant).build();
        setId(user, USER_ID);

        EduFlowUserDetails principal = new EduFlowUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
        );
    }

    // ── Provisioning ─────────────────────────────────────────────────────────

    @Test
    void provision_whenValidRequest_seedsLimitsFromPlanAndPublishesCreated() {
        when(tenantRepository.existsBySlug("acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            setId(t, TENANT_ID);
            return t;
        });

        CreateTenantRequest req = CreateTenantRequest.builder()
                .name("Acme").slug("acme").plan(TenantPlan.PROFESSIONAL)
                .primaryContactName("Ann").primaryContactEmail("ann@acme.com")
                .build();

        TenantResponse response = tenantService.provision(req);

        assertThat(response.getMaxStudents()).isEqualTo(2_000);
        assertThat(response.getMaxStaffUsers()).isEqualTo(25);
        assertThat(response.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(settingsRepository).save(any(TenantSettings.class));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TenantEvents.TenantCreated.class);
    }

    @Test
    void provision_whenSlugExists_throwsDuplicateSlugException() {
        when(tenantRepository.existsBySlug("taken")).thenReturn(true);

        CreateTenantRequest req = CreateTenantRequest.builder()
                .name("Dup").slug("taken").plan(TenantPlan.STARTER)
                .primaryContactName("X").primaryContactEmail("x@d.com").build();

        assertThatThrownBy(() -> tenantService.provision(req))
                .isInstanceOf(DuplicateSlugException.class)
                .hasMessageContaining("taken");
        verify(tenantRepository, never()).save(any());
    }

    // ── Status transitions ────────────────────────────────────────────────────

    @Test
    void changeStatus_activeToSuspendedWithoutReason_throws() {
        Tenant tenant = activeTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        ChangeTenantStatusRequest req = ChangeTenantStatusRequest.builder()
                .status(TenantStatus.SUSPENDED).build();

        assertThatThrownBy(() -> tenantService.changeStatus(TENANT_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void changeStatus_suspendedToInactive_throwsInvalidTransition() {
        Tenant tenant = activeTenant();
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        ChangeTenantStatusRequest req = ChangeTenantStatusRequest.builder()
                .status(TenantStatus.INACTIVE).build();

        assertThatThrownBy(() -> tenantService.changeStatus(TENANT_ID, req))
                .isInstanceOf(InvalidTenantStatusTransitionException.class);
    }

    @Test
    void changeStatus_activeToSuspended_stampsReasonAndPublishes() {
        Tenant tenant = activeTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeTenantStatusRequest req = ChangeTenantStatusRequest.builder()
                .status(TenantStatus.SUSPENDED).reason("non-payment").build();

        TenantResponse response = tenantService.changeStatus(TENANT_ID, req);

        assertThat(response.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(response.getSuspensionReason()).isEqualTo("non-payment");
        assertThat(response.getSuspendedAt()).isNotNull();
        verify(events).publishEvent(any(TenantEvents.TenantStatusChanged.class));
    }

    @Test
    void changeStatus_inactiveToActive_clearsStamps() {
        Tenant tenant = activeTenant();
        tenant.setStatus(TenantStatus.INACTIVE);
        tenant.setDeactivatedAt(java.time.Instant.now());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeTenantStatusRequest req = ChangeTenantStatusRequest.builder()
                .status(TenantStatus.ACTIVE).build();

        TenantResponse response = tenantService.changeStatus(TENANT_ID, req);

        assertThat(response.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(response.getDeactivatedAt()).isNull();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Tenant activeTenant() {
        Tenant tenant = Tenant.builder()
                .name("Acme").slug("acme").status(TenantStatus.ACTIVE)
                .plan(TenantPlan.STARTER).build();
        setId(tenant, TENANT_ID);
        return tenant;
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = com.eduflow.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id on " + entity.getClass().getSimpleName(), e);
        }
    }
}
