package com.eduflow.university;

import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.Tenant;
import com.eduflow.university.dto.CourseRequest;
import com.eduflow.university.dto.CourseResponse;
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
 * Unit tests for {@link CourseService}. The {@link UniversityService} dependency is
 * mocked to control tenant-scoped university resolution.
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final UUID TENANT_ID     = UUID.randomUUID();
    private static final UUID USER_ID       = UUID.randomUUID();
    private static final UUID UNIVERSITY_ID = UUID.randomUUID();
    private static final UUID COURSE_ID     = UUID.randomUUID();

    @Mock CourseRepository    courseRepository;
    @Mock UniversityService   universityService;
    @Mock AuditService        auditService;

    @InjectMocks CourseService courseService;

    private Tenant tenant;
    private University university;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        university = University.builder().tenant(tenant).name("Oxford").country("UK").active(true).build();
        setId(university, UNIVERSITY_ID);

        User user = User.builder().tenant(tenant).build();
        setId(user, USER_ID);

        EduFlowUserDetails principal = new EduFlowUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void create_whenUniversityResolves_savesAndAudits() {
        when(universityService.findOrThrow(UNIVERSITY_ID, TENANT_ID)).thenReturn(university);

        Course saved = Course.builder()
                .tenant(tenant).university(university)
                .name("MSc CS").level(CourseLevel.MASTER).active(true).build();
        setId(saved, COURSE_ID);
        when(courseRepository.save(any(Course.class))).thenReturn(saved);

        CourseRequest req = CourseRequest.builder().name("MSc CS").level(CourseLevel.MASTER).build();
        CourseResponse response = courseService.create(UNIVERSITY_ID, req);

        assertThat(response.getName()).isEqualTo("MSc CS");
        assertThat(response.getUniversityName()).isEqualTo("Oxford");
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("COURSE_CREATED"), eq("COURSE"), eq(COURSE_ID));
    }

    @Test
    void create_whenUniversityNotInTenant_throws() {
        when(universityService.findOrThrow(UNIVERSITY_ID, TENANT_ID))
                .thenThrow(new UniversityNotFoundException(UNIVERSITY_ID));

        CourseRequest req = CourseRequest.builder().name("MSc CS").level(CourseLevel.MASTER).build();
        assertThatThrownBy(() -> courseService.create(UNIVERSITY_ID, req))
                .isInstanceOf(UniversityNotFoundException.class);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void update_whenFound_appliesChangesAndAudits() {
        Course existing = Course.builder()
                .tenant(tenant).university(university)
                .name("Old").level(CourseLevel.BACHELOR).active(true).build();
        setId(existing, COURSE_ID);
        when(courseRepository.findByIdAndTenantId(COURSE_ID, TENANT_ID)).thenReturn(Optional.of(existing));
        when(courseRepository.save(existing)).thenReturn(existing);

        CourseRequest req = CourseRequest.builder().name("New").level(CourseLevel.MASTER).build();
        CourseResponse response = courseService.update(COURSE_ID, req);

        assertThat(response.getName()).isEqualTo("New");
        assertThat(response.getLevel()).isEqualTo(CourseLevel.MASTER);
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID),
                eq("COURSE_UPDATED"), eq("COURSE"), eq(COURSE_ID));
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
