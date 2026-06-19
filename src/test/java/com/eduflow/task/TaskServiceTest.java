package com.eduflow.task;

import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.Student;
import com.eduflow.student.StudentRepository;
import com.eduflow.tenant.Tenant;
import com.eduflow.user.User;
import com.eduflow.workflow.StageType;
import com.eduflow.workflow.WorkflowStage;
import com.eduflow.workflow.WorkflowStageRepository;
import com.eduflow.workflow.event.WorkflowEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

/** Unit tests for {@link TaskService}. */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID STAGE_ID   = UUID.randomUUID();
    private static final UUID INSTANCE_ID = UUID.randomUUID();
    private static final UUID TASK_ID    = UUID.randomUUID();

    @Mock TaskRepository taskRepository;
    @Mock StudentRepository studentRepository;
    @Mock WorkflowStageRepository stageRepository;
    @Mock AuditService auditService;

    @InjectMocks TaskService service;

    private Tenant tenant;
    private Student student;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);
        User user = User.builder().tenant(tenant).build();
        setId(user, USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new EduFlowUserDetails(user), null, List.of()));
        student = Student.builder().tenant(tenant).firstName("A").lastName("B").email("a@b.com").build();
        setId(student, STUDENT_ID);
    }

    @Test
    void generateForStage_withOwnerRole_createsAssignedTask() {
        WorkflowStage stage = WorkflowStage.builder()
                .tenant(tenant).name("Document Collection").code("DOC").displayOrder(1)
                .stageType(StageType.DOCUMENT_STAGE).ownerRole("ROLE_DOC_OFFICER").slaDays(7).build();
        setId(stage, STAGE_ID);
        when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generateForStage(new WorkflowEvents.StageEntered(
                TENANT_ID, USER_ID, INSTANCE_ID, STUDENT_ID, STAGE_ID, true));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task t = captor.getValue();
        assertThat(t.getAssignedRole()).isEqualTo("ROLE_DOC_OFFICER");
        assertThat(t.getTitle()).isEqualTo("Document Collection");
        assertThat(t.getDueAt()).isNotNull();
        assertThat(t.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void generateForStage_withoutOwnerRole_createsNothing() {
        WorkflowStage stage = WorkflowStage.builder()
                .tenant(tenant).name("Lead").code("LEAD").displayOrder(1).stageType(StageType.NORMAL).build();
        setId(stage, STAGE_ID);
        when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage));

        service.generateForStage(new WorkflowEvents.StageEntered(
                TENANT_ID, USER_ID, INSTANCE_ID, STUDENT_ID, STAGE_ID, true));

        verify(taskRepository, never()).save(any());
    }

    @Test
    void complete_marksCompletedWithTimestamp() {
        Task task = Task.builder().tenant(tenant).student(student)
                .title("X").status(TaskStatus.IN_PROGRESS).priority(TaskPriority.MEDIUM).build();
        setId(task, TASK_ID);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.complete(TASK_ID);

        assertThat(resp.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(resp.getCompletedAt()).isNotNull();
    }

    @Test
    void start_whenAlreadyInProgress_throws() {
        Task task = Task.builder().tenant(tenant).student(student)
                .title("X").status(TaskStatus.IN_PROGRESS).priority(TaskPriority.MEDIUM).build();
        setId(task, TASK_ID);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.start(TASK_ID))
                .isInstanceOf(InvalidTaskStatusTransitionException.class);
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
