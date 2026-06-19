package com.eduflow.report;

import com.eduflow.report.dto.WorkflowDashboardMetrics;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.task.TaskRepository;
import com.eduflow.task.TaskStatus;
import com.eduflow.tenant.Tenant;
import com.eduflow.user.User;
import com.eduflow.workflow.InstanceStatus;
import com.eduflow.workflow.StudentWorkflow;
import com.eduflow.workflow.StudentWorkflowRepository;
import com.eduflow.workflow.WorkflowTemplateRepository;
import com.eduflow.workflow.dto.StageDistribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Unit tests for {@link WorkflowAnalyticsService}. */
@ExtendWith(MockitoExtension.class)
class WorkflowAnalyticsServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock WorkflowTemplateRepository templateRepository;
    @Mock StudentWorkflowRepository instanceRepository;
    @Mock TaskRepository taskRepository;

    @InjectMocks WorkflowAnalyticsService service;

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);
        User user = User.builder().tenant(tenant).build();
        setId(user, UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new EduFlowUserDetails(user), null, List.of()));
    }

    @Test
    void dashboard_composesCountsStageDistributionAndAvgCompletion() {
        when(templateRepository.countByTenantIdAndActiveTrueAndArchivedFalse(TENANT_ID)).thenReturn(2L);
        when(instanceRepository.countByTenantIdAndStatus(TENANT_ID, InstanceStatus.ACTIVE)).thenReturn(5L);
        when(instanceRepository.countByTenantIdAndStatus(TENANT_ID, InstanceStatus.COMPLETED)).thenReturn(3L);
        when(instanceRepository.countByTenantIdAndStatusAndSlaBreachedTrue(TENANT_ID, InstanceStatus.ACTIVE)).thenReturn(1L);
        when(taskRepository.countByTenantIdAndStatus(TENANT_ID, TaskStatus.PENDING)).thenReturn(4L);
        when(taskRepository.countByTenantIdAndStatus(TENANT_ID, TaskStatus.IN_PROGRESS)).thenReturn(2L);
        when(taskRepository.countByTenantIdAndStatus(TENANT_ID, TaskStatus.COMPLETED)).thenReturn(7L);
        when(instanceRepository.countActivePerStage(TENANT_ID)).thenReturn(List.of(
                new StageDistribution(UUID.randomUUID(), "Lead", "#000", 3),
                new StageDistribution(UUID.randomUUID(), "Enrolled", "#0f0", 2)));

        Instant now = Instant.now();
        StudentWorkflow completed = StudentWorkflow.builder()
                .startedAt(now.minus(10, ChronoUnit.DAYS)).completedAt(now).build();
        when(instanceRepository.findByTenantIdAndStatus(TENANT_ID, InstanceStatus.COMPLETED))
                .thenReturn(List.of(completed));

        WorkflowDashboardMetrics m = service.dashboard();

        assertThat(m.activeWorkflows()).isEqualTo(2);
        assertThat(m.activeInstances()).isEqualTo(5);
        assertThat(m.completedInstances()).isEqualTo(3);
        assertThat(m.slaViolations()).isEqualTo(1);
        assertThat(m.pendingTasks()).isEqualTo(6);     // PENDING + IN_PROGRESS
        assertThat(m.completedTasks()).isEqualTo(7);
        assertThat(m.maxStageCount()).isEqualTo(3);
        assertThat(m.averageCompletionDays()).isEqualTo(10L);
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = com.eduflow.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
