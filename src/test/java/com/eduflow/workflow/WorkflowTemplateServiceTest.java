package com.eduflow.workflow;

import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.tenant.TenantSettings;
import com.eduflow.tenant.TenantSettingsRepository;
import com.eduflow.user.User;
import com.eduflow.workflow.dto.WorkflowStageRequest;
import com.eduflow.workflow.dto.WorkflowTemplateRequest;
import com.eduflow.workflow.dto.WorkflowTransitionRequest;
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
 * Unit tests for {@link WorkflowTemplateService}. Dependencies are mocked; the
 * {@link SecurityContextHolder} is seeded with a tenant-scoped principal.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowTemplateServiceTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID USER_ID     = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Mock WorkflowTemplateRepository templateRepository;
    @Mock WorkflowStageRepository stageRepository;
    @Mock WorkflowTransitionRepository transitionRepository;
    @Mock TenantRepository tenantRepository;
    @Mock TenantSettingsRepository tenantSettingsRepository;
    @Mock AuditService auditService;

    @InjectMocks WorkflowTemplateService service;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);
        User user = User.builder().tenant(tenant).build();
        setId(user, USER_ID);
        EduFlowUserDetails principal = new EduFlowUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_whenNameAlreadyExists_throwsDuplicateWorkflowName() {
        when(templateRepository.existsByTenantIdAndName(TENANT_ID, "Australia"))
                .thenReturn(true);

        WorkflowTemplateRequest req = WorkflowTemplateRequest.builder().name("Australia").build();

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(DuplicateWorkflowNameException.class)
                .hasMessageContaining("Australia");
        verify(templateRepository, never()).save(any());
    }

    @Test
    void create_whenValid_savesVersion1AndAudits() {
        when(templateRepository.existsByTenantIdAndName(TENANT_ID, "Australia")).thenReturn(false);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowTemplate saved = service.create(
                WorkflowTemplateRequest.builder().name("Australia").build());

        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.isActive()).isTrue();
        verify(auditService).publish(eq(TENANT_ID), eq(USER_ID), anyString(), eq("WORKFLOW"), any());
    }

    // ── clone ─────────────────────────────────────────────────────────────────

    @Test
    void clone_copiesStagesAndTransitionsWithBumpedVersion() {
        WorkflowTemplate source = WorkflowTemplate.builder().tenant(tenant).name("Australia").version(1).build();
        setId(source, TEMPLATE_ID);

        WorkflowStage s1 = stage(source, "Lead", "LEAD", 1, StageType.NORMAL);
        WorkflowStage s2 = stage(source, "Enrolled", "ENROLLED", 2, StageType.FINAL_STAGE);
        source.getStages().add(s1);
        source.getStages().add(s2);
        WorkflowTransition tr = WorkflowTransition.builder()
                .tenant(tenant).template(source).fromStage(s1).toStage(s2)
                .transitionType(TransitionType.FORWARD).build();
        source.getTransitions().add(tr);

        when(templateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(source));
        when(templateRepository.findFirstByTenantIdAndNameOrderByVersionDesc(TENANT_ID, "Australia"))
                .thenReturn(Optional.of(source));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowTemplate copy = service.clone(TEMPLATE_ID);

        assertThat(copy.getVersion()).isEqualTo(2);
        assertThat(copy.getStages()).hasSize(2);
        assertThat(copy.getTransitions()).hasSize(1);
        // Transitions must be rewired to the *copied* stages, not the source ones.
        assertThat(copy.getTransitions().get(0).getFromStage()).isIn(copy.getStages());
        assertThat(copy.getTransitions().get(0).getToStage()).isIn(copy.getStages());
    }

    // ── setDefault ──────────────────────────────────────────────────────────────

    @Test
    void setDefault_clearsOtherDefaultsAndSyncsTenantSettings() {
        WorkflowTemplate target = WorkflowTemplate.builder().tenant(tenant).name("A").build();
        setId(target, TEMPLATE_ID);
        UUID otherId = UUID.randomUUID();
        WorkflowTemplate other = WorkflowTemplate.builder().tenant(tenant).name("B").defaultTemplate(true).build();
        setId(other, otherId);

        when(templateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(target));
        when(templateRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(target, other));
        TenantSettings settings = TenantSettings.builder().tenantId(TENANT_ID).build();
        when(tenantSettingsRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(settings));

        service.setDefault(TEMPLATE_ID);

        assertThat(target.isDefaultTemplate()).isTrue();
        assertThat(other.isDefaultTemplate()).isFalse();
        assertThat(settings.getDefaultWorkflowTemplateId()).isEqualTo(TEMPLATE_ID);
    }

    @Test
    void setDefault_whenArchived_throws() {
        WorkflowTemplate archived = WorkflowTemplate.builder().tenant(tenant).name("A").archived(true).build();
        setId(archived, TEMPLATE_ID);
        when(templateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.setDefault(TEMPLATE_ID))
                .isInstanceOf(WorkflowArchivedException.class);
    }

    // ── graph validation ─────────────────────────────────────────────────────

    @Test
    void validateGraph_whenNoFinalStage_throws() {
        WorkflowTemplate t = WorkflowTemplate.builder().tenant(tenant).name("A").build();
        setId(t, TEMPLATE_ID);
        t.getStages().add(stage(t, "Lead", "LEAD", 1, StageType.NORMAL));
        when(templateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.validateGraph(TEMPLATE_ID))
                .isInstanceOf(InvalidWorkflowGraphException.class)
                .hasMessageContaining("FINAL_STAGE");
    }

    @Test
    void validateGraph_whenStageUnreachable_throws() {
        WorkflowTemplate t = WorkflowTemplate.builder().tenant(tenant).name("A").build();
        setId(t, TEMPLATE_ID);
        WorkflowStage entry = stage(t, "Lead", "LEAD", 1, StageType.NORMAL);
        WorkflowStage orphan = stage(t, "Visa", "VISA", 2, StageType.NORMAL);
        WorkflowStage end = stage(t, "Enrolled", "ENROLLED", 3, StageType.FINAL_STAGE);
        t.getStages().addAll(List.of(entry, orphan, end));
        // entry → end, but orphan has no inbound edge.
        t.getTransitions().add(WorkflowTransition.builder()
                .tenant(tenant).template(t).fromStage(entry).toStage(end).build());
        when(templateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.validateGraph(TEMPLATE_ID))
                .isInstanceOf(InvalidWorkflowGraphException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    void validateGraph_whenConnected_passes() {
        WorkflowTemplate t = WorkflowTemplate.builder().tenant(tenant).name("A").build();
        setId(t, TEMPLATE_ID);
        WorkflowStage entry = stage(t, "Lead", "LEAD", 1, StageType.NORMAL);
        WorkflowStage end = stage(t, "Enrolled", "ENROLLED", 2, StageType.FINAL_STAGE);
        t.getStages().addAll(List.of(entry, end));
        t.getTransitions().add(WorkflowTransition.builder()
                .tenant(tenant).template(t).fromStage(entry).toStage(end).build());
        when(templateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(t));

        service.validateGraph(TEMPLATE_ID);     // no exception
    }

    @Test
    void addTransition_whenDuplicate_throws() {
        WorkflowTemplate t = WorkflowTemplate.builder().tenant(tenant).name("A").build();
        setId(t, TEMPLATE_ID);
        WorkflowStage a = stage(t, "A", "A", 1, StageType.NORMAL);
        WorkflowStage b = stage(t, "B", "B", 2, StageType.FINAL_STAGE);
        t.getStages().addAll(List.of(a, b));
        when(templateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(t));
        when(transitionRepository.existsByTemplateIdAndFromStageIdAndToStageId(TEMPLATE_ID, a.getId(), b.getId()))
                .thenReturn(true);

        WorkflowTransitionRequest req = WorkflowTransitionRequest.builder()
                .fromStageId(a.getId()).toStageId(b.getId()).build();

        assertThatThrownBy(() -> service.addTransition(TEMPLATE_ID, req))
                .isInstanceOf(InvalidWorkflowGraphException.class)
                .hasMessageContaining("already exists");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private WorkflowStage stage(WorkflowTemplate t, String name, String code, int order, StageType type) {
        WorkflowStage s = WorkflowStage.builder()
                .tenant(tenant).template(t).name(name).code(code)
                .displayOrder(order).stageType(type).build();
        setId(s, UUID.randomUUID());
        return s;
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = com.eduflow.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id on " + entity.getClass().getSimpleName(), e);
        }
    }
}
