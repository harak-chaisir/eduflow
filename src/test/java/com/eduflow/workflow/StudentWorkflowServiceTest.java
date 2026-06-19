package com.eduflow.workflow;

import com.eduflow.audit.AuditService;
import com.eduflow.document.DocumentService;
import com.eduflow.document.DocumentType;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.Student;
import com.eduflow.student.StudentRepository;
import com.eduflow.tenant.Tenant;
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
 * Unit tests for {@link StudentWorkflowService} — assignment, transition gating, completion.
 */
@ExtendWith(MockitoExtension.class)
class StudentWorkflowServiceTest {

    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID INSTANCE_ID = UUID.randomUUID();

    @Mock StudentWorkflowRepository instanceRepository;
    @Mock WorkflowStageHistoryRepository historyRepository;
    @Mock WorkflowTemplateRepository templateRepository;
    @Mock WorkflowTransitionRepository transitionRepository;
    @Mock StudentRepository studentRepository;
    @Mock DocumentService documentService;
    @Mock AuditService auditService;
    @Mock org.springframework.context.ApplicationEventPublisher events;

    @InjectMocks StudentWorkflowService service;

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
    void assignDefault_whenNoDefaultTemplate_returnsEmpty() {
        when(instanceRepository.findByStudentIdAndStatus(STUDENT_ID, InstanceStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(templateRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        assertThat(service.assignDefault(STUDENT_ID)).isEmpty();
        verify(instanceRepository, never()).save(any());
    }

    @Test
    void assignWorkflow_createsInstanceAtEntryStageAndOpensHistory() {
        WorkflowTemplate template = template();
        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID)).thenReturn(Optional.of(student));
        when(templateRepository.findByIdAndTenantId(template.getId(), TENANT_ID)).thenReturn(Optional.of(template));
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StudentWorkflow instance = service.assignWorkflow(STUDENT_ID, template.getId());

        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.ACTIVE);
        assertThat(instance.getCurrentStage().getCode()).isEqualTo("LEAD");   // lowest display order
        verify(historyRepository).save(any(WorkflowStageHistory.class));
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID), anyString(), eq("STUDENT_WORKFLOW"), any());
    }

    @Test
    void moveStage_whenRequiredDocsMissing_throws() {
        WorkflowTemplate template = template();
        WorkflowStage lead = template.getStages().get(0);
        lead.setRequiredDocuments(List.of(DocumentType.PASSPORT));
        WorkflowStage enrolled = template.getStages().get(1);
        StudentWorkflow instance = activeInstance(template, lead);

        when(instanceRepository.findByIdAndTenantId(INSTANCE_ID, TENANT_ID)).thenReturn(Optional.of(instance));
        when(transitionRepository.findByTemplateIdAndFromStageIdAndToStageId(
                template.getId(), lead.getId(), enrolled.getId()))
                .thenReturn(Optional.of(forward(template, lead, enrolled)));
        when(documentService.listDocuments(STUDENT_ID)).thenReturn(List.of());   // nothing approved

        assertThatThrownBy(() -> service.moveStage(INSTANCE_ID, enrolled.getId(), null))
                .isInstanceOf(RequiredDocumentsMissingException.class);
        verify(instanceRepository, never()).save(any());
    }

    @Test
    void moveStage_toFinalStage_completesInstance() {
        WorkflowTemplate template = template();
        WorkflowStage lead = template.getStages().get(0);
        WorkflowStage enrolled = template.getStages().get(1);     // FINAL_STAGE
        StudentWorkflow instance = activeInstance(template, lead);

        when(instanceRepository.findByIdAndTenantId(INSTANCE_ID, TENANT_ID)).thenReturn(Optional.of(instance));
        when(transitionRepository.findByTemplateIdAndFromStageIdAndToStageId(
                template.getId(), lead.getId(), enrolled.getId()))
                .thenReturn(Optional.of(forward(template, lead, enrolled)));
        when(historyRepository.findFirstByStudentWorkflowIdAndExitedAtIsNull(INSTANCE_ID))
                .thenReturn(Optional.empty());
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StudentWorkflow moved = service.moveStage(INSTANCE_ID, enrolled.getId(), "done");

        assertThat(moved.getCurrentStage().getCode()).isEqualTo("ENROLLED");
        assertThat(moved.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(moved.getCompletedAt()).isNotNull();
    }

    @Test
    void moveStage_whenNoTransition_throws() {
        WorkflowTemplate template = template();
        WorkflowStage lead = template.getStages().get(0);
        WorkflowStage enrolled = template.getStages().get(1);
        StudentWorkflow instance = activeInstance(template, lead);

        when(instanceRepository.findByIdAndTenantId(INSTANCE_ID, TENANT_ID)).thenReturn(Optional.of(instance));
        when(transitionRepository.findByTemplateIdAndFromStageIdAndToStageId(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveStage(INSTANCE_ID, enrolled.getId(), null))
                .isInstanceOf(InvalidWorkflowTransitionException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private WorkflowTemplate template() {
        WorkflowTemplate t = WorkflowTemplate.builder().tenant(tenant).name("Australia").defaultTemplate(true).build();
        setId(t, UUID.randomUUID());
        WorkflowStage lead = stage(t, "Lead", "LEAD", 1, StageType.NORMAL);
        WorkflowStage enrolled = stage(t, "Enrolled", "ENROLLED", 2, StageType.FINAL_STAGE);
        t.getStages().add(lead);
        t.getStages().add(enrolled);
        return t;
    }

    private StudentWorkflow activeInstance(WorkflowTemplate template, WorkflowStage current) {
        StudentWorkflow w = StudentWorkflow.builder()
                .tenant(tenant).student(student).template(template).currentStage(current)
                .status(InstanceStatus.ACTIVE).build();
        setId(w, INSTANCE_ID);
        return w;
    }

    private WorkflowTransition forward(WorkflowTemplate t, WorkflowStage from, WorkflowStage to) {
        WorkflowTransition tr = WorkflowTransition.builder()
                .tenant(tenant).template(t).fromStage(from).toStage(to)
                .transitionType(TransitionType.FORWARD).build();
        setId(tr, UUID.randomUUID());
        return tr;
    }

    private WorkflowStage stage(WorkflowTemplate t, String name, String code, int order, StageType type) {
        WorkflowStage s = WorkflowStage.builder()
                .tenant(tenant).template(t).name(name).code(code).displayOrder(order).stageType(type).build();
        setId(s, UUID.randomUUID());
        return s;
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
