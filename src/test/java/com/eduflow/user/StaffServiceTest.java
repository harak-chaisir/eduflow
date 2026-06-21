package com.eduflow.user;

import com.eduflow.audit.AuditService;
import com.eduflow.common.BaseEntity;
import com.eduflow.role.Role;
import com.eduflow.role.RoleRepository;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantLimitService;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.user.dto.InviteStaffRequest;
import com.eduflow.user.dto.StaffInviteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StaffService}. The Spring context is not loaded; dependencies are
 * mocked and the {@link SecurityContextHolder} is populated to simulate an authenticated,
 * tenant-scoped admin user (id {@link #USER_ID}, tenant {@link #TENANT_ID}).
 */
@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock TenantRepository tenantRepository;
    @Mock TenantLimitService tenantLimitService;
    @Mock PasswordResetService passwordResetService;
    @Mock AuditService auditService;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock com.eduflow.student.StudentRepository studentRepository;
    @Mock com.eduflow.audit.AuditEventRepository auditEventRepository;

    @InjectMocks StaffService staffService;

    @BeforeEach
    void setUpSecurityContext() {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);
        User user = User.builder().tenant(tenant).build();
        setId(user, USER_ID);

        EduFlowUserDetails principal = new EduFlowUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    // ── inviteStaff ────────────────────────────────────────────────────────────

    @Test
    void inviteStaff_whenEmailAlreadyExists_throwsDuplicateStaffException() {
        when(userRepository.existsByEmailIgnoreCaseAndTenantId("jane@test.com", TENANT_ID))
                .thenReturn(true);

        InviteStaffRequest req = InviteStaffRequest.builder()
                .email("jane@test.com")
                .roleNames(Set.of("ROLE_COUNSELOR"))
                .build();

        assertThatThrownBy(() -> staffService.inviteStaff(req))
                .isInstanceOf(DuplicateStaffException.class)
                .hasMessageContaining("jane@test.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void inviteStaff_whenValid_createsPendingUserAndIssuesToken() {
        when(userRepository.existsByEmailIgnoreCaseAndTenantId("jane@test.com", TENANT_ID))
                .thenReturn(false);

        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(roleRepository.findByName("ROLE_COUNSELOR")).thenReturn(Optional.of(role("ROLE_COUNSELOR")));
        when(passwordEncoder.encode(any())).thenReturn("placeholder-hash");

        User saved = User.builder().tenant(tenant).email("jane@test.com")
                .status(UserStatus.PENDING_VERIFICATION).roles(new HashSet<>()).build();
        setId(saved, TARGET_ID);
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(passwordResetService.createToken(saved)).thenReturn("tok-123");

        InviteStaffRequest req = InviteStaffRequest.builder()
                .email("jane@test.com").firstName("Jane").lastName("Doe")
                .roleNames(Set.of("ROLE_COUNSELOR")).build();

        StaffInviteResult result = staffService.inviteStaff(req);

        assertThat(result.token()).isEqualTo("tok-123");
        assertThat(result.staff().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        verify(tenantLimitService).assertCanAddStaff(TENANT_ID);
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("STAFF_INVITED"), eq("STAFF"), eq(TARGET_ID));
    }

    @Test
    void inviteStaff_whenRoleNotAssignable_throwsIllegalArgument() {
        when(userRepository.existsByEmailIgnoreCaseAndTenantId("x@test.com", TENANT_ID)).thenReturn(false);
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        InviteStaffRequest req = InviteStaffRequest.builder()
                .email("x@test.com").roleNames(Set.of("ROLE_SUPER_ADMIN")).build();

        assertThatThrownBy(() -> staffService.inviteStaff(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not assignable");
        verify(userRepository, never()).save(any());
    }

    // ── setStatus ──────────────────────────────────────────────────────────────

    @Test
    void setStatus_whenDeactivatingSelf_throwsIllegalArgument() {
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(activeUser(USER_ID)));

        assertThatThrownBy(() -> staffService.setStatus(USER_ID, UserStatus.INACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own");
        verify(userRepository, never()).save(any());
    }

    @Test
    void setStatus_whenDeactivatingLastActiveAdmin_throwsLastTenantAdminException() {
        User admin = activeUser(TARGET_ID);
        admin.getRoles().add(role("ROLE_TENANT_ADMIN"));
        when(userRepository.findByIdAndTenantId(TARGET_ID, TENANT_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findActiveByTenantIdAndRoleName(TENANT_ID, "ROLE_TENANT_ADMIN"))
                .thenReturn(List.of(admin));

        assertThatThrownBy(() -> staffService.setStatus(TARGET_ID, UserStatus.INACTIVE))
                .isInstanceOf(LastTenantAdminException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void setStatus_whenDeactivatingNonAdmin_setsInactiveAndAudits() {
        User member = activeUser(TARGET_ID);
        member.getRoles().add(role("ROLE_COUNSELOR"));
        when(userRepository.findByIdAndTenantId(TARGET_ID, TENANT_ID)).thenReturn(Optional.of(member));
        when(userRepository.save(member)).thenReturn(member);

        staffService.setStatus(TARGET_ID, UserStatus.INACTIVE);

        assertThat(member.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("STAFF_STATUS_CHANGED"), eq("STAFF"), eq(TARGET_ID), eq("ACTIVE"), eq("INACTIVE"));
    }

    @Test
    void setStatus_whenInvalidTargetStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> staffService.setStatus(TARGET_ID, UserStatus.LOCKED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTIVE or INACTIVE");
    }

    // ── updateStaff ────────────────────────────────────────────────────────────

    @Test
    void updateStaff_whenRemovingAdminFromLastActiveAdmin_throwsLastTenantAdminException() {
        User admin = activeUser(TARGET_ID);
        admin.getRoles().add(role("ROLE_TENANT_ADMIN"));
        when(userRepository.findByIdAndTenantId(TARGET_ID, TENANT_ID)).thenReturn(Optional.of(admin));
        when(roleRepository.findByName("ROLE_COUNSELOR")).thenReturn(Optional.of(role("ROLE_COUNSELOR")));
        when(userRepository.findActiveByTenantIdAndRoleName(TENANT_ID, "ROLE_TENANT_ADMIN"))
                .thenReturn(List.of(admin));

        com.eduflow.user.dto.UpdateStaffRequest req = com.eduflow.user.dto.UpdateStaffRequest.builder()
                .firstName("A").lastName("B").roleNames(Set.of("ROLE_COUNSELOR")).build();

        assertThatThrownBy(() -> staffService.updateStaff(TARGET_ID, req))
                .isInstanceOf(LastTenantAdminException.class);
        verify(userRepository, never()).save(any());
    }

    // ── getStaffStats ────────────────────────────────────────────────────────────

    @Test
    void getStaffStats_computesAverageCaseloadAndSeats() {
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        User counselor1 = activeUser(c1);
        User counselor2 = activeUser(c2);

        when(userRepository.countByTenantId(TENANT_ID)).thenReturn(5L);
        when(userRepository.findActiveByTenantIdAndRoleName(TENANT_ID, "ROLE_COUNSELOR"))
                .thenReturn(List.of(counselor1, counselor2));
        when(studentRepository.countActiveCaseloadByCounselor(TENANT_ID))
                .thenReturn(List.of(new Object[]{c1, 10L}, new Object[]{c2, 20L}));

        Tenant tenant = Tenant.builder().maxStaffUsers(25).build();
        setId(tenant, TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        var stats = staffService.getStaffStats();

        assertThat(stats.total()).isEqualTo(5);
        assertThat(stats.activeCounselors()).isEqualTo(2);
        assertThat(stats.avgCaseload()).isEqualTo(15);   // (10 + 20) / 2
        assertThat(stats.seatsMax()).isEqualTo(25);
        assertThat(stats.seatsAvailable()).isEqualTo(20);
    }

    // ── getCaseload ──────────────────────────────────────────────────────────────

    @Test
    void getCaseload_forCounselor_buildsPipelineAndStudents() {
        User counselor = activeUser(TARGET_ID);
        counselor.getRoles().add(role("ROLE_COUNSELOR"));
        when(userRepository.findByIdAndTenantId(TARGET_ID, TENANT_ID)).thenReturn(Optional.of(counselor));

        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);
        when(studentRepository.findByAssignedCounselorIdAndTenantIdAndStatusNotOrderByFirstNameAsc(
                TARGET_ID, TENANT_ID, com.eduflow.student.StudentStatus.INACTIVE))
                .thenReturn(List.of(
                        student(tenant, "Amy", com.eduflow.student.StudentStatus.LEAD),
                        student(tenant, "Ben", com.eduflow.student.StudentStatus.ACTIVE)));

        var view = staffService.getCaseload(TARGET_ID);

        assertThat(view.hasCaseload()).isTrue();
        assertThat(view.count()).isEqualTo(2);
        assertThat(view.students()).hasSize(2);
        assertThat(view.pipeline()).anySatisfy(p -> {
            if (p.status() == com.eduflow.student.StudentStatus.LEAD) assertThat(p.count()).isEqualTo(1);
            if (p.status() == com.eduflow.student.StudentStatus.ACTIVE) assertThat(p.count()).isEqualTo(1);
        });
    }

    @Test
    void getCaseload_forNonCounselor_isEmpty() {
        User officer = activeUser(TARGET_ID);
        officer.getRoles().add(role("ROLE_DOC_OFFICER"));
        when(userRepository.findByIdAndTenantId(TARGET_ID, TENANT_ID)).thenReturn(Optional.of(officer));

        var view = staffService.getCaseload(TARGET_ID);

        assertThat(view.hasCaseload()).isFalse();
        assertThat(view.count()).isZero();
    }

    // ── getStaff ───────────────────────────────────────────────────────────────

    @Test
    void getStaff_whenNotFound_throwsStaffNotFoundException() {
        when(userRepository.findByIdAndTenantId(TARGET_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.getStaff(TARGET_ID))
                .isInstanceOf(StaffNotFoundException.class)
                .hasMessageContaining(TARGET_ID.toString());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User activeUser(UUID id) {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);
        User u = User.builder().tenant(tenant).email("u@test.com")
                .status(UserStatus.ACTIVE).roles(new HashSet<>()).build();
        setId(u, id);
        return u;
    }

    private static Role role(String name) {
        Role r = Role.builder().name(name).build();
        setId(r, UUID.randomUUID());
        return r;
    }

    private static com.eduflow.student.Student student(Tenant tenant, String firstName,
                                                       com.eduflow.student.StudentStatus status) {
        com.eduflow.student.Student s = com.eduflow.student.Student.builder()
                .tenant(tenant).firstName(firstName).lastName("Test")
                .email(firstName.toLowerCase() + "@test.com").status(status).build();
        setId(s, UUID.randomUUID());
        return s;
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id on " + entity.getClass().getSimpleName(), e);
        }
    }
}
