package com.eduflow.application;

import com.eduflow.student.Student;
import com.eduflow.student.StudentRepository;
import com.eduflow.student.StudentStatus;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.university.Course;
import com.eduflow.university.CourseLevel;
import com.eduflow.university.CourseRepository;
import com.eduflow.university.University;
import com.eduflow.university.UniversityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link ApplicationRepository}, asserting tenant isolation.
 * Runs against the configured PostgreSQL; changes roll back via {@link Transactional}.
 */
@SpringBootTest
@Transactional
class ApplicationRepositoryTest {

    @Autowired ApplicationRepository applicationRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired TenantRepository tenantRepository;

    private UUID tenantAId;
    private UUID tenantBId;
    private Application appA;

    @BeforeEach
    void setUp() {
        // Avoid a leaked SecurityContext from prior unit tests interfering with JPA auditing.
        SecurityContextHolder.clearContext();
        Tenant a = tenantRepository.save(Tenant.builder().name("Tenant A").slug("ta-" + suffix()).build());
        Tenant b = tenantRepository.save(Tenant.builder().name("Tenant B").slug("tb-" + suffix()).build());
        tenantAId = a.getId();
        tenantBId = b.getId();

        Student student = studentRepository.save(Student.builder()
                .tenant(a).firstName("Ann").lastName("Lee").email("ann-" + suffix() + "@t.com")
                .status(StudentStatus.ACTIVE).build());
        University uni = universityRepository.save(University.builder()
                .tenant(a).name("Oxford").country("UK").active(true).build());
        Course course = courseRepository.save(Course.builder()
                .tenant(a).university(uni).name("MSc CS").level(CourseLevel.MASTER).active(true).build());

        appA = applicationRepository.save(Application.builder()
                .tenant(a).student(student).course(course).status(ApplicationStatus.DRAFT).build());
    }

    @Test
    void findByIdAndTenantId_whenForeignTenant_returnsEmpty() {
        Optional<Application> sameTenant = applicationRepository.findByIdAndTenantId(appA.getId(), tenantAId);
        Optional<Application> foreignTenant = applicationRepository.findByIdAndTenantId(appA.getId(), tenantBId);

        assertThat(sameTenant).isPresent();
        assertThat(foreignTenant).isEmpty();
    }

    @Test
    void existsByStudentIdAndCourseIdAndTenantId_isTenantScoped() {
        UUID studentId = appA.getStudent().getId();
        UUID courseId = appA.getCourse().getId();

        assertThat(applicationRepository
                .existsByStudentIdAndCourseIdAndTenantId(studentId, courseId, tenantAId)).isTrue();
        assertThat(applicationRepository
                .existsByStudentIdAndCourseIdAndTenantId(studentId, courseId, tenantBId)).isFalse();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
