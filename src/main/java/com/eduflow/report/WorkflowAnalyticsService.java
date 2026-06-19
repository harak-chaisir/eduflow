package com.eduflow.report;

import com.eduflow.report.dto.WorkflowDashboardMetrics;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.task.TaskRepository;
import com.eduflow.task.TaskStatus;
import com.eduflow.workflow.InstanceStatus;
import com.eduflow.workflow.StudentWorkflow;
import com.eduflow.workflow.StudentWorkflowRepository;
import com.eduflow.workflow.WorkflowTemplateRepository;
import com.eduflow.workflow.dto.StageDistribution;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregation for the workflow dashboard and analytics (PRD §15, §16).
 * All queries are tenant-scoped via the authenticated principal (NFR-1).
 */
@Service
@RequiredArgsConstructor
public class WorkflowAnalyticsService {

    private final WorkflowTemplateRepository templateRepository;
    private final StudentWorkflowRepository instanceRepository;
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public WorkflowDashboardMetrics dashboard() {
        UUID tenantId = resolvedTenantId();

        long activeWorkflows = templateRepository.countByTenantIdAndActiveTrueAndArchivedFalse(tenantId);
        long activeInstances = instanceRepository.countByTenantIdAndStatus(tenantId, InstanceStatus.ACTIVE);
        long completedInstances = instanceRepository.countByTenantIdAndStatus(tenantId, InstanceStatus.COMPLETED);
        long slaViolations = instanceRepository.countByTenantIdAndStatusAndSlaBreachedTrue(tenantId, InstanceStatus.ACTIVE);
        long pendingTasks = taskRepository.countByTenantIdAndStatus(tenantId, TaskStatus.PENDING)
                + taskRepository.countByTenantIdAndStatus(tenantId, TaskStatus.IN_PROGRESS);
        long completedTasks = taskRepository.countByTenantIdAndStatus(tenantId, TaskStatus.COMPLETED);

        List<StageDistribution> perStage = instanceRepository.countActivePerStage(tenantId);
        long maxStageCount = perStage.stream().mapToLong(StageDistribution::count).max().orElse(0);

        Long avgDays = averageCompletionDays(tenantId);

        return new WorkflowDashboardMetrics(activeWorkflows, activeInstances, completedInstances,
                slaViolations, pendingTasks, completedTasks, avgDays, perStage, maxStageCount);
    }

    /** Average days from start to completion across completed instances, or null if none. */
    private Long averageCompletionDays(UUID tenantId) {
        List<StudentWorkflow> completed = instanceRepository
                .findByTenantIdAndStatus(tenantId, InstanceStatus.COMPLETED);
        var stats = completed.stream()
                .filter(w -> w.getCompletedAt() != null && w.getStartedAt() != null)
                .mapToLong(w -> Duration.between(w.getStartedAt(), w.getCompletedAt()).toDays())
                .summaryStatistics();
        return stats.getCount() == 0 ? null : Math.round(stats.getAverage());
    }

    private UUID resolvedTenantId() {
        EduFlowUserDetails principal = (EduFlowUserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getTenantId();
    }
}
