package com.eduflow.student;

import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.dto.RegisterStudentRequest;
import com.eduflow.student.dto.StudentResponse;
import com.eduflow.student.dto.UpdateStudentRequest;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantLimitService;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.user.User;
import com.eduflow.user.UserRepository;
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
 * Unit tests for {@link StudentService}.
 *
 * <p>The Spring context is not loaded; dependencies are mocked with Mockito.
 * The {@link SecurityContextHolder} is populated before each test to simulate
 * an authenticated tenant-scoped user.</p>
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();

    @Mock StudentRepository studentRepository;
    @Mock TenantRepository  tenantRepository;
    @Mock UserRepository    userRepository;
    @Mock AuditService      auditService;
    @Mock TenantLimitService tenantLimitService;

    @InjectMocks StudentService studentService;

    @BeforeEach
    void setUpSecurityContext() {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        User user = User.builder().tenant(tenant).build();
        setId(user, USER_ID);

        EduFlowUserDetails principal = new EduFlowUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }

    // ── registerStudent ───────────────────────────────────────────────────────

    @Test
    void registerStudent_whenEmailAlreadyExists_throwsDuplicateStudentException() {
        when(studentRepository.existsByEmailAndTenantId("alice@test.com", TENANT_ID))
                .thenReturn(true);

        RegisterStudentRequest req = RegisterStudentRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@test.com")
                .build();

        assertThatThrownBy(() -> studentService.registerStudent(req))
                .isInstanceOf(DuplicateStudentException.class)
                .hasMessageContaining("alice@test.com");

        verify(studentRepository, never()).save(any());
    }

    @Test
    void registerStudent_whenValidRequest_returnsStudentResponse() {
        when(studentRepository.existsByEmailAndTenantId("bob@test.com", TENANT_ID))
                .thenReturn(false);

        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        Student savedStudent = Student.builder()
                .tenant(tenant)
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@test.com")
                .status(StudentStatus.LEAD)
                .build();
        setId(savedStudent, STUDENT_ID);

        when(studentRepository.save(any(Student.class))).thenReturn(savedStudent);

        RegisterStudentRequest req = RegisterStudentRequest.builder()
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@test.com")
                .build();

        StudentResponse response = studentService.registerStudent(req);

        assertThat(response.getEmail()).isEqualTo("bob@test.com");
        assertThat(response.getStatus()).isEqualTo(StudentStatus.LEAD);
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("STUDENT_CREATED"), eq("STUDENT"), eq(STUDENT_ID));
    }

    // ── getStudent ────────────────────────────────────────────────────────────

    @Test
    void getStudent_whenNotFound_throwsStudentNotFoundException() {
        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.getStudent(STUDENT_ID))
                .isInstanceOf(StudentNotFoundException.class)
                .hasMessageContaining(STUDENT_ID.toString());
    }

    @Test
    void getStudent_whenFound_returnsStudentResponse() {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        Student student = Student.builder()
                .tenant(tenant)
                .firstName("Carol")
                .lastName("Davis")
                .email("carol@test.com")
                .status(StudentStatus.ACTIVE)
                .build();
        setId(student, STUDENT_ID);

        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID))
                .thenReturn(Optional.of(student));

        StudentResponse response = studentService.getStudent(STUDENT_ID);

        assertThat(response.getId()).isEqualTo(STUDENT_ID);
        assertThat(response.getFirstName()).isEqualTo("Carol");
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_whenValidTransition_updatesStatus() {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        Student student = Student.builder()
                .tenant(tenant)
                .firstName("Dan")
                .lastName("Evans")
                .email("dan@test.com")
                .status(StudentStatus.LEAD)
                .build();
        setId(student, STUDENT_ID);

        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID))
                .thenReturn(Optional.of(student));
        when(studentRepository.save(student)).thenReturn(student);

        StudentResponse response = studentService.updateStatus(STUDENT_ID, StudentStatus.QUALIFIED);

        assertThat(response.getStatus()).isEqualTo(StudentStatus.QUALIFIED);
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("STUDENT_STATUS_CHANGED"), eq("STUDENT"), eq(STUDENT_ID),
                eq("LEAD"), eq("QUALIFIED"));
    }

    @Test
    void updateStatus_whenInvalidTransition_throwsInvalidStudentStatusTransitionException() {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        Student student = Student.builder()
                .tenant(tenant)
                .firstName("Eve")
                .lastName("Foster")
                .email("eve@test.com")
                .status(StudentStatus.LEAD)
                .build();
        setId(student, STUDENT_ID);

        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID))
                .thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentService.updateStatus(STUDENT_ID, StudentStatus.ENROLLED))
                .isInstanceOf(InvalidStudentStatusTransitionException.class)
                .hasMessageContaining("LEAD")
                .hasMessageContaining("ENROLLED");

        verify(studentRepository, never()).save(any());
    }

    // ── deleteStudent ─────────────────────────────────────────────────────────

    @Test
    void deleteStudent_whenAlreadyInactive_isNoOp() {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        Student student = Student.builder()
                .tenant(tenant)
                .firstName("Frank")
                .lastName("Green")
                .email("frank@test.com")
                .status(StudentStatus.INACTIVE)
                .build();
        setId(student, STUDENT_ID);

        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID))
                .thenReturn(Optional.of(student));

        studentService.deleteStudent(STUDENT_ID);

        verify(studentRepository, never()).save(any());
        verify(auditService, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void deleteStudent_whenActive_setsStatusInactive() {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        Student student = Student.builder()
                .tenant(tenant)
                .firstName("Grace")
                .lastName("Hall")
                .email("grace@test.com")
                .status(StudentStatus.ACTIVE)
                .build();
        setId(student, STUDENT_ID);

        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID))
                .thenReturn(Optional.of(student));
        when(studentRepository.save(student)).thenReturn(student);

        studentService.deleteStudent(STUDENT_ID);

        assertThat(student.getStatus()).isEqualTo(StudentStatus.INACTIVE);
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("STUDENT_DELETED"), eq("STUDENT"), eq(STUDENT_ID));
    }

    // ── updateStudent ─────────────────────────────────────────────────────────

    @Test
    void updateStudent_whenEmailTaken_throwsDuplicateStudentException() {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        Student student = Student.builder()
                .tenant(tenant)
                .firstName("Hank")
                .lastName("Irving")
                .email("hank@test.com")
                .status(StudentStatus.LEAD)
                .build();
        setId(student, STUDENT_ID);

        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID))
                .thenReturn(Optional.of(student));
        when(studentRepository.existsByEmailAndTenantId("taken@test.com", TENANT_ID))
                .thenReturn(true);

        UpdateStudentRequest req = UpdateStudentRequest.builder()
                .email("taken@test.com")
                .build();

        assertThatThrownBy(() -> studentService.updateStudent(STUDENT_ID, req))
                .isInstanceOf(DuplicateStudentException.class);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Reflectively sets the {@code id} field inherited from {@link com.eduflow.common.BaseEntity}
     * so we can create fully initialised entity stubs without a database.
     */
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

