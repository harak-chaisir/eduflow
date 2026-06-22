package com.eduflow.university;

import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.university.dto.UniversityRequest;
import com.eduflow.university.dto.UniversityResponse;
import com.eduflow.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UniversityService}. Dependencies are mocked; the security
 * context is populated to simulate an authenticated tenant-scoped user.
 */
@ExtendWith(MockitoExtension.class)
class UniversityServiceTest {

    private static final UUID TENANT_ID     = UUID.randomUUID();
    private static final UUID USER_ID       = UUID.randomUUID();
    private static final UUID UNIVERSITY_ID = UUID.randomUUID();

    @Mock UniversityRepository universityRepository;
    @Mock TenantRepository     tenantRepository;
    @Mock AuditService         auditService;

    @InjectMocks UniversityService universityService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        User user = User.builder().tenant(tenant).build();
        setId(user, USER_ID);

        EduFlowUserDetails principal = new EduFlowUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void create_whenValid_savesAndAuditsAndReturnsResponse() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        University saved = University.builder()
                .tenant(tenant).name("Oxford").country("UK").active(true).build();
        setId(saved, UNIVERSITY_ID);
        when(universityRepository.save(any(University.class))).thenReturn(saved);

        UniversityRequest req = UniversityRequest.builder().name("Oxford").country("UK").build();
        UniversityResponse response = universityService.create(req);

        assertThat(response.getName()).isEqualTo("Oxford");
        assertThat(response.isActive()).isTrue();
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("UNIVERSITY_CREATED"), eq("UNIVERSITY"), eq(UNIVERSITY_ID));
    }

    @Test
    void get_whenNotFoundInTenant_throwsUniversityNotFoundException() {
        when(universityRepository.findByIdAndTenantId(UNIVERSITY_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> universityService.get(UNIVERSITY_ID))
                .isInstanceOf(UniversityNotFoundException.class)
                .hasMessageContaining(UNIVERSITY_ID.toString());
    }

    @Test
    void update_whenFound_appliesChangesAndAudits() {
        University existing = University.builder()
                .tenant(tenant).name("Old").country("UK").active(true).build();
        setId(existing, UNIVERSITY_ID);
        when(universityRepository.findByIdAndTenantId(UNIVERSITY_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(universityRepository.save(existing)).thenReturn(existing);

        UniversityRequest req = UniversityRequest.builder().name("New name").active(false).build();
        UniversityResponse response = universityService.update(UNIVERSITY_ID, req);

        assertThat(response.getName()).isEqualTo("New name");
        assertThat(response.isActive()).isFalse();
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("UNIVERSITY_UPDATED"), eq("UNIVERSITY"), eq(UNIVERSITY_ID));
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = com.eduflow.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }
}
