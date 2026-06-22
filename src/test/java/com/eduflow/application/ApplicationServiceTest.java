package com.eduflow.application;

import com.eduflow.application.dto.ApplicationResponse;
import com.eduflow.application.dto.CreateApplicationRequest;
import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.Student;
import com.eduflow.student.StudentNotFoundException;
import com.eduflow.student.StudentRepository;
import com.eduflow.tenant.Tenant;
import com.eduflow.university.Course;
import com.eduflow.university.CourseLevel;
import com.eduflow.university.CourseRepository;
import com.eduflow.university.University;
import com.eduflow.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * Unit tests for {@link ApplicationService}, focusing on the status state machine,
 * date side effects, duplicate guarding and tenant-scoped resolution.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final UUID TENANT_ID      = UUID.randomUUID();
    private static final UUID USER_ID        = UUID.randomUUID();
    private static final UUID STUDENT_ID     = UUID.randomUUID();
    private static final UUID COURSE_ID      = UUID.randomUUID();
    private static final UUID APPLICATION_ID = UUID.randomUUID();

    @Mock ApplicationRepository applicationRepository;
    @Mock StudentRepository     studentRepository;
    @Mock CourseRepository      courseRepository;
    @Mock AuditService          auditService;

    @InjectMocks ApplicationService applicationService;

    private Tenant tenant;
    private Student student;
    private Course course;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        student = Student.builder().tenant(tenant).firstName("Ann").lastName("Lee").email("a@t.com").build();
        setId(student, STUDENT_ID);

        University university = University.builder().tenant(tenant).name("Oxford").country("UK").build();
        setId(university, UUID.randomUUID());
        course = Course.builder().tenant(tenant).university(university)
                .name("MSc CS").level(CourseLevel.MASTER).build();
        setId(course, COURSE_ID);

        User user = User.builder().tenant(tenant).build();
        setId(user, USER_ID);

        EduFlowUserDetails principal = new EduFlowUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void createApplication_whenValid_createsDraftAndAudits() {
        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID)).thenReturn(Optional.of(student));
        when(courseRepository.findByIdAndTenantId(COURSE_ID, TENANT_ID)).thenReturn(Optional.of(course));
        when(applicationRepository.existsByStudentIdAndCourseIdAndTenantId(STUDENT_ID, COURSE_ID, TENANT_ID))
                .thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> {
            Application a = inv.getArgument(0);
            setId(a, APPLICATION_ID);
            return a;
        });

        CreateApplicationRequest req = CreateApplicationRequest.builder().courseId(COURSE_ID).notes("n").build();
        ApplicationResponse response = applicationService.createApplication(STUDENT_ID, req);

        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(response.getStudentName()).isEqualTo("Ann Lee");
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("APPLICATION_CREATED"), eq("APPLICATION"), eq(APPLICATION_ID));
    }

    @Test
    void createApplication_whenDuplicate_throwsDuplicateApplicationException() {
        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID)).thenReturn(Optional.of(student));
        when(courseRepository.findByIdAndTenantId(COURSE_ID, TENANT_ID)).thenReturn(Optional.of(course));
        when(applicationRepository.existsByStudentIdAndCourseIdAndTenantId(STUDENT_ID, COURSE_ID, TENANT_ID))
                .thenReturn(true);

        CreateApplicationRequest req = CreateApplicationRequest.builder().courseId(COURSE_ID).build();
        assertThatThrownBy(() -> applicationService.createApplication(STUDENT_ID, req))
                .isInstanceOf(DuplicateApplicationException.class);

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void createApplication_whenStudentNotInTenant_throws() {
        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID)).thenReturn(Optional.empty());

        CreateApplicationRequest req = CreateApplicationRequest.builder().courseId(COURSE_ID).build();
        assertThatThrownBy(() -> applicationService.createApplication(STUDENT_ID, req))
                .isInstanceOf(StudentNotFoundException.class);
    }

    // ── status transitions ────────────────────────────────────────────────────

    @Test
    void updateStatus_draftToSubmitted_setsAppliedDate() {
        Application app = applicationWithStatus(ApplicationStatus.DRAFT);
        when(applicationRepository.findByIdAndTenantId(APPLICATION_ID, TENANT_ID)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);

        ApplicationResponse response = applicationService.updateStatus(APPLICATION_ID, ApplicationStatus.SUBMITTED);

        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(app.getAppliedDate()).isNotNull();
        assertThat(app.getDecisionDate()).isNull();
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("APPLICATION_STATUS_CHANGED"), eq("APPLICATION"), eq(APPLICATION_ID),
                eq("DRAFT"), eq("SUBMITTED"));
    }

    @Test
    void updateStatus_underReviewToUnconditionalOffer_setsDecisionDate() {
        Application app = applicationWithStatus(ApplicationStatus.UNDER_REVIEW);
        when(applicationRepository.findByIdAndTenantId(APPLICATION_ID, TENANT_ID)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);

        applicationService.updateStatus(APPLICATION_ID, ApplicationStatus.UNCONDITIONAL_OFFER);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.UNCONDITIONAL_OFFER);
        assertThat(app.getDecisionDate()).isNotNull();
    }

    @Test
    void updateStatus_underReviewToRejected_setsDecisionDate() {
        Application app = applicationWithStatus(ApplicationStatus.UNDER_REVIEW);
        when(applicationRepository.findByIdAndTenantId(APPLICATION_ID, TENANT_ID)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);

        applicationService.updateStatus(APPLICATION_ID, ApplicationStatus.REJECTED);

        assertThat(app.getDecisionDate()).isNotNull();
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @CsvSource({
            "DRAFT,UNDER_REVIEW",
            "DRAFT,UNCONDITIONAL_OFFER",
            "DRAFT,REJECTED",
            "SUBMITTED,CONDITIONAL_OFFER",
            "SUBMITTED,UNCONDITIONAL_OFFER",
            "UNDER_REVIEW,SUBMITTED",
            "CONDITIONAL_OFFER,UNDER_REVIEW",
            "CONDITIONAL_OFFER,SUBMITTED",
            "UNCONDITIONAL_OFFER,REJECTED",
            "UNCONDITIONAL_OFFER,UNDER_REVIEW",
            "REJECTED,SUBMITTED",
            "REJECTED,UNCONDITIONAL_OFFER"
    })
    void updateStatus_illegalTransition_throws(ApplicationStatus from, ApplicationStatus to) {
        Application app = applicationWithStatus(from);
        when(applicationRepository.findByIdAndTenantId(APPLICATION_ID, TENANT_ID)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> applicationService.updateStatus(APPLICATION_ID, to))
                .isInstanceOf(InvalidApplicationStatusTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());

        verify(applicationRepository, never()).save(any());
    }

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "DRAFT,SUBMITTED",
            "SUBMITTED,UNDER_REVIEW",
            "SUBMITTED,REJECTED",
            "UNDER_REVIEW,CONDITIONAL_OFFER",
            "UNDER_REVIEW,UNCONDITIONAL_OFFER",
            "UNDER_REVIEW,REJECTED",
            "CONDITIONAL_OFFER,UNCONDITIONAL_OFFER",
            "CONDITIONAL_OFFER,REJECTED"
    })
    void updateStatus_legalTransition_succeeds(ApplicationStatus from, ApplicationStatus to) {
        Application app = applicationWithStatus(from);
        when(applicationRepository.findByIdAndTenantId(APPLICATION_ID, TENANT_ID)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);

        ApplicationResponse response = applicationService.updateStatus(APPLICATION_ID, to);

        assertThat(response.getStatus()).isEqualTo(to);
    }

    @Test
    void updateStatus_whenNotFound_throwsApplicationNotFoundException() {
        when(applicationRepository.findByIdAndTenantId(APPLICATION_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.updateStatus(APPLICATION_ID, ApplicationStatus.SUBMITTED))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Application applicationWithStatus(ApplicationStatus status) {
        Application app = Application.builder()
                .tenant(tenant).student(student).course(course).status(status).build();
        setId(app, APPLICATION_ID);
        return app;
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
