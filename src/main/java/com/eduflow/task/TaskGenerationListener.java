package com.eduflow.task;

import com.eduflow.workflow.event.WorkflowEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Generates a task whenever a student enters a workflow stage (PRD §14). Runs
 * synchronously within the publishing transaction so the task is visible as soon as the
 * stage move commits.
 */
@Component
@RequiredArgsConstructor
public class TaskGenerationListener {

    private final TaskService taskService;

    @EventListener
    public void onStageEntered(WorkflowEvents.StageEntered event) {
        taskService.generateForStage(event);
    }
}
