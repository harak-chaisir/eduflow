package com.eduflow.workflow;

import com.eduflow.tenant.Tenant;
import com.eduflow.workflow.event.WorkflowEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for {@link WorkflowSlaService} using a fixed clock. */
@ExtendWith(MockitoExtension.class)
class WorkflowSlaServiceTest {

    @Mock StudentWorkflowRepository instanceRepository;
    @Mock ApplicationEventPublisher events;

    private final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void scan_whenStageOverSla_flagsBreachAndPublishes() {
        WorkflowSlaService service = new WorkflowSlaService(instanceRepository, events, clock);
        StudentWorkflow instance = instanceEnteredDaysAgo(10, 7);   // 10 days in a 7-day SLA stage

        when(instanceRepository.findByStatus(InstanceStatus.ACTIVE)).thenReturn(List.of(instance));
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int flagged = service.scan();

        assertThat(flagged).isEqualTo(1);
        assertThat(instance.isSlaBreached()).isTrue();
        verify(events).publishEvent(any(WorkflowEvents.SlaBreached.class));
    }

    @Test
    void scan_whenWithinSla_doesNotFlag() {
        WorkflowSlaService service = new WorkflowSlaService(instanceRepository, events, clock);
        StudentWorkflow instance = instanceEnteredDaysAgo(2, 7);

        when(instanceRepository.findByStatus(InstanceStatus.ACTIVE)).thenReturn(List.of(instance));

        int flagged = service.scan();

        assertThat(flagged).isZero();
        assertThat(instance.isSlaBreached()).isFalse();
        verify(events, never()).publishEvent(any());
    }

    private StudentWorkflow instanceEnteredDaysAgo(int daysAgo, int slaDays) {
        Tenant tenant = Tenant.builder().build();
        setId(tenant, UUID.randomUUID());
        WorkflowStage stage = WorkflowStage.builder()
                .tenant(tenant).name("Stage").code("S").displayOrder(1)
                .stageType(StageType.NORMAL).slaDays(slaDays).build();
        setId(stage, UUID.randomUUID());
        com.eduflow.student.Student student = com.eduflow.student.Student.builder().tenant(tenant).build();
        setId(student, UUID.randomUUID());
        StudentWorkflow w = StudentWorkflow.builder()
                .tenant(tenant).student(student).currentStage(stage)
                .status(InstanceStatus.ACTIVE)
                .currentStageEnteredAt(NOW.minus(daysAgo, ChronoUnit.DAYS))
                .build();
        setId(w, UUID.randomUUID());
        return w;
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
