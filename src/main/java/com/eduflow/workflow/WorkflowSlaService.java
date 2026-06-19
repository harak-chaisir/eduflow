package com.eduflow.workflow;

import com.eduflow.workflow.event.WorkflowEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Detects SLA breaches on active workflow instances (PRD §12, FR-8).
 *
 * <p>Runs as a scheduled, system-wide job (no security principal — iterates instances
 * directly across tenants). For each active instance whose time in the current stage
 * exceeds the stage's {@code slaDays}, it persists {@code slaBreached=true} and publishes
 * a {@link WorkflowEvents.SlaBreached} event (consumed by notifications). Complements the
 * on-read computation in {@code StudentWorkflowResponse}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowSlaService {

    private final StudentWorkflowRepository instanceRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Nightly at 02:00 (overridable via {@code eduflow.workflow.sla-cron}). */
    @Scheduled(cron = "${eduflow.workflow.sla-cron:0 0 2 * * *}")
    @Transactional
    public void detectBreaches() {
        int newlyBreached = scan();
        if (newlyBreached > 0) {
            log.info("SLA scan flagged {} newly-breached workflow instance(s)", newlyBreached);
        }
    }

    /**
     * Scans active instances and flags newly-breached ones. Returns the count flagged in
     * this run. Exposed (package-public) so it can be invoked directly from tests.
     */
    @Transactional
    public int scan() {
        Instant now = clock.instant();
        List<StudentWorkflow> active = instanceRepository.findByStatus(InstanceStatus.ACTIVE);
        int count = 0;
        for (StudentWorkflow instance : active) {
            WorkflowStage stage = instance.getCurrentStage();
            if (stage == null || stage.getSlaDays() == null) {
                continue;
            }
            long days = Duration.between(instance.getCurrentStageEnteredAt(), now).toDays();
            boolean breached = days > stage.getSlaDays();
            if (breached && !instance.isSlaBreached()) {
                instance.setSlaBreached(true);
                instanceRepository.save(instance);
                events.publishEvent(new WorkflowEvents.SlaBreached(
                        instance.getTenant().getId(), instance.getId(),
                        instance.getStudent().getId(), stage.getId()));
                count++;
            } else if (!breached && instance.isSlaBreached()) {
                // Stage changed / SLA no longer breached — clear the flag.
                instance.setSlaBreached(false);
                instanceRepository.save(instance);
            }
        }
        return count;
    }
}
