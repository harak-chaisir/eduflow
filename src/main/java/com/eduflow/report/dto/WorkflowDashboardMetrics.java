package com.eduflow.report.dto;

import com.eduflow.workflow.dto.StageDistribution;

import java.util.List;

/**
 * Tenant-admin workflow dashboard metrics (PRD §15) plus the students-by-stage
 * distribution (PRD §16).
 */
public record WorkflowDashboardMetrics(
        long activeWorkflows,
        long activeInstances,
        long completedInstances,
        long slaViolations,
        long pendingTasks,
        long completedTasks,
        Long averageCompletionDays,
        List<StageDistribution> studentsPerStage,
        long maxStageCount) {
}
