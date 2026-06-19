package com.eduflow.workflow;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantNotFoundException;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.tenant.TenantSettings;
import com.eduflow.tenant.TenantSettingsRepository;
import com.eduflow.workflow.dto.WorkflowSearchCriteria;
import com.eduflow.workflow.dto.WorkflowStageRequest;
import com.eduflow.workflow.dto.WorkflowTemplateRequest;
import com.eduflow.workflow.dto.WorkflowTemplateResponse;
import com.eduflow.workflow.dto.WorkflowTransitionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builder-side service for the Workflow Management module: create, configure, version,
 * and lifecycle-manage tenant workflow definitions (PRD §5–§9, FR-1..FR-5, FR-9).
 *
 * <p>All methods resolve {@code tenantId} from the authenticated principal — never from a
 * request parameter. Stage/transition mutations are validated against the workflow graph
 * and audited.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowTemplateService {

    private final WorkflowTemplateRepository templateRepository;
    private final WorkflowStageRepository stageRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final AuditService auditService;

    // ── Create / Update ────────────────────────────────────────────────────────

    /** Creates a new, empty workflow template (version 1) for the calling tenant (FR-1). */
    public WorkflowTemplate create(WorkflowTemplateRequest request) {
        UUID tenantId = resolvedTenantId();
        if (templateRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new DuplicateWorkflowNameException(request.getName());
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        WorkflowTemplate template = WorkflowTemplate.builder()
                .tenant(tenant)
                .name(request.getName())
                .description(request.getDescription())
                .country(request.getCountry())
                .version(1)
                .active(request.getActive() == null || request.getActive())
                .build();

        WorkflowTemplate saved = templateRepository.save(template);
        log.info("Workflow template '{}' created with id {} for tenant {}", saved.getName(), saved.getId(), tenantId);
        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_CREATED, "WORKFLOW", saved.getId());
        return saved;
    }

    /** Updates an existing template's editable properties. */
    public WorkflowTemplate update(UUID templateId, WorkflowTemplateRequest request) {
        UUID tenantId = resolvedTenantId();
        WorkflowTemplate template = findTemplateOrThrow(templateId, tenantId);

        if (!template.getName().equals(request.getName())
                && templateRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new DuplicateWorkflowNameException(request.getName());
        }
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setCountry(request.getCountry());
        if (request.getActive() != null) {
            template.setActive(request.getActive());
        }
        WorkflowTemplate saved = templateRepository.save(template);
        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_UPDATED, "WORKFLOW", templateId);
        return saved;
    }

    // ── Clone (FR-2) ─────────────────────────────────────────────────────────────

    /**
     * Deep-copies a template — stages, their required documents, and all transitions —
     * into a new template with {@code version} bumped to the next free value for the name.
     */
    public WorkflowTemplate clone(UUID templateId) {
        UUID tenantId = resolvedTenantId();
        WorkflowTemplate source = findTemplateOrThrow(templateId, tenantId);

        int nextVersion = templateRepository
                .findFirstByTenantIdAndNameOrderByVersionDesc(tenantId, source.getName())
                .map(t -> t.getVersion() + 1)
                .orElse(source.getVersion() + 1);

        WorkflowTemplate copy = WorkflowTemplate.builder()
                .tenant(source.getTenant())
                .name(source.getName())
                .description(source.getDescription())
                .country(source.getCountry())
                .version(nextVersion)
                .active(true)
                .build();

        // Copy stages, keeping a map old-stage-id → new stage to rewire transitions.
        Map<UUID, WorkflowStage> stageByOldId = new HashMap<>();
        for (WorkflowStage src : source.getStages()) {
            WorkflowStage s = WorkflowStage.builder()
                    .tenant(source.getTenant())
                    .template(copy)
                    .name(src.getName())
                    .code(src.getCode())
                    .displayOrder(src.getDisplayOrder())
                    .description(src.getDescription())
                    .color(src.getColor())
                    .active(src.isActive())
                    .slaDays(src.getSlaDays())
                    .stageType(src.getStageType())
                    .ownerRole(src.getOwnerRole())
                    .requiredDocuments(new ArrayList<>(src.getRequiredDocuments()))
                    .build();
            copy.getStages().add(s);
            stageByOldId.put(src.getId(), s);
        }
        for (WorkflowTransition src : source.getTransitions()) {
            WorkflowTransition t = WorkflowTransition.builder()
                    .tenant(source.getTenant())
                    .template(copy)
                    .fromStage(stageByOldId.get(src.getFromStage().getId()))
                    .toStage(stageByOldId.get(src.getToStage().getId()))
                    .transitionType(src.getTransitionType())
                    .label(src.getLabel())
                    .requiresApproval(src.isRequiresApproval())
                    .build();
            copy.getTransitions().add(t);
        }

        WorkflowTemplate saved = templateRepository.save(copy);
        log.info("Workflow template {} cloned to {} (v{})", templateId, saved.getId(), nextVersion);
        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_CLONED, "WORKFLOW", saved.getId(),
                templateId.toString(), String.valueOf(saved.getId()));
        return saved;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public void deactivate(UUID templateId) {
        UUID tenantId = resolvedTenantId();
        WorkflowTemplate template = findTemplateOrThrow(templateId, tenantId);
        template.setActive(false);
        templateRepository.save(template);
        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_DEACTIVATED, "WORKFLOW", templateId);
    }

    /** Archives a template: blocks new assignments; existing instances keep running (FR-9). */
    public void archive(UUID templateId) {
        UUID tenantId = resolvedTenantId();
        WorkflowTemplate template = findTemplateOrThrow(templateId, tenantId);
        template.setArchived(true);
        template.setActive(false);
        if (template.isDefaultTemplate()) {
            template.setDefaultTemplate(false);
            clearTenantDefaultIfMatches(tenantId, templateId);
        }
        templateRepository.save(template);
        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_ARCHIVED, "WORKFLOW", templateId);
    }

    /** Marks a template as the tenant default (single default), syncing tenant settings. */
    public void setDefault(UUID templateId) {
        UUID tenantId = resolvedTenantId();
        WorkflowTemplate template = findTemplateOrThrow(templateId, tenantId);
        if (template.isArchived()) {
            throw new WorkflowArchivedException(templateId);
        }
        // Clear the flag on any current default, then set it on this one.
        for (WorkflowTemplate other : templateRepository.findByTenantId(tenantId)) {
            if (other.isDefaultTemplate() && !other.getId().equals(templateId)) {
                other.setDefaultTemplate(false);
                templateRepository.save(other);
            }
        }
        template.setDefaultTemplate(true);
        templateRepository.save(template);

        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        settings.setDefaultWorkflowTemplateId(templateId);
        tenantSettingsRepository.save(settings);

        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_SET_DEFAULT, "WORKFLOW", templateId);
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<WorkflowTemplate> search(WorkflowSearchCriteria criteria, Pageable pageable) {
        UUID tenantId = resolvedTenantId();
        return templateRepository.findAll(WorkflowSpecification.from(criteria, tenantId), pageable);
    }

    /**
     * Search returning header DTOs. The {@code WorkflowTemplateResponse::from} mapping reads the
     * lazy {@code stages} collection for {@code stageCount}, so it must run inside this transaction —
     * mapping in the controller would throw {@code LazyInitializationException}.
     */
    @Transactional(readOnly = true)
    public Page<WorkflowTemplateResponse> searchAsResponses(WorkflowSearchCriteria criteria, Pageable pageable) {
        return search(criteria, pageable).map(WorkflowTemplateResponse::from);
    }

    /** Active, non-archived templates for the calling tenant — the set a student may be assigned. */
    @Transactional(readOnly = true)
    public List<WorkflowTemplate> listAssignable() {
        return templateRepository.findByTenantId(resolvedTenantId()).stream()
                .filter(WorkflowTemplate::isActive)
                .filter(t -> !t.isArchived())
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    /** Loads a template with its stages + transitions initialised for the builder view. */
    @Transactional(readOnly = true)
    public WorkflowTemplate getWithGraph(UUID templateId) {
        UUID tenantId = resolvedTenantId();
        WorkflowTemplate template = findTemplateOrThrow(templateId, tenantId);
        template.getStages().size();        // force-init within the tx
        template.getTransitions().size();
        template.getStages().forEach(s -> s.getRequiredDocuments().size());
        return template;
    }

    // ── Stage configuration (FR-3, §10–§12) ──────────────────────────────────────

    public WorkflowStage addStage(UUID templateId, WorkflowStageRequest request) {
        UUID tenantId = resolvedTenantId();
        WorkflowTemplate template = findTemplateOrThrow(templateId, tenantId);
        if (stageRepository.existsByTemplateIdAndCode(templateId, request.getCode())) {
            throw new InvalidWorkflowGraphException(
                    "Stage code '" + request.getCode() + "' already exists in this workflow");
        }
        int order = request.getDisplayOrder() != null
                ? request.getDisplayOrder()
                : template.getStages().stream().mapToInt(WorkflowStage::getDisplayOrder).max().orElse(0) + 1;

        WorkflowStage stage = WorkflowStage.builder()
                .tenant(template.getTenant())
                .template(template)
                .name(request.getName())
                .code(request.getCode())
                .displayOrder(order)
                .description(request.getDescription())
                .color(request.getColor())
                .active(request.getActive() == null || request.getActive())
                .slaDays(request.getSlaDays())
                .stageType(request.getStageType() != null ? request.getStageType() : StageType.NORMAL)
                .ownerRole(emptyToNull(request.getOwnerRole()))
                .requiredDocuments(request.getRequiredDocuments() != null
                        ? new ArrayList<>(request.getRequiredDocuments()) : new ArrayList<>())
                .build();

        WorkflowStage saved = stageRepository.save(stage);
        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_STAGE_SAVED, "WORKFLOW_STAGE", saved.getId());
        return saved;
    }

    public WorkflowStage updateStage(UUID stageId, WorkflowStageRequest request) {
        UUID tenantId = resolvedTenantId();
        WorkflowStage stage = stageRepository.findByIdAndTenantId(stageId, tenantId)
                .orElseThrow(() -> new WorkflowTemplateNotFoundException("Stage not found: " + stageId));
        stage.setName(request.getName());
        stage.setCode(request.getCode());
        if (request.getDisplayOrder() != null) stage.setDisplayOrder(request.getDisplayOrder());
        stage.setDescription(request.getDescription());
        stage.setColor(request.getColor());
        if (request.getActive() != null) stage.setActive(request.getActive());
        stage.setSlaDays(request.getSlaDays());
        if (request.getStageType() != null) stage.setStageType(request.getStageType());
        stage.setOwnerRole(emptyToNull(request.getOwnerRole()));
        stage.setRequiredDocuments(request.getRequiredDocuments() != null
                ? new ArrayList<>(request.getRequiredDocuments()) : new ArrayList<>());
        WorkflowStage saved = stageRepository.save(stage);
        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_STAGE_SAVED, "WORKFLOW_STAGE", stageId);
        return saved;
    }

    public void deleteStage(UUID stageId) {
        UUID tenantId = resolvedTenantId();
        WorkflowStage stage = stageRepository.findByIdAndTenantId(stageId, tenantId)
                .orElseThrow(() -> new WorkflowTemplateNotFoundException("Stage not found: " + stageId));
        // Remove any transitions touching this stage first (FKs cascade, but keep the
        // in-memory template graph consistent for callers within this tx).
        transitionRepository.findByTemplateId(stage.getTemplate().getId()).stream()
                .filter(t -> t.getFromStage().getId().equals(stageId) || t.getToStage().getId().equals(stageId))
                .forEach(transitionRepository::delete);
        stageRepository.delete(stage);
        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_STAGE_DELETED, "WORKFLOW_STAGE", stageId);
    }

    // ── Transition configuration (FR-4, §9) ──────────────────────────────────────

    public WorkflowTransition addTransition(UUID templateId, WorkflowTransitionRequest request) {
        UUID tenantId = resolvedTenantId();
        WorkflowTemplate template = findTemplateOrThrow(templateId, tenantId);

        WorkflowStage from = requireStageInTemplate(request.getFromStageId(), template);
        WorkflowStage to = requireStageInTemplate(request.getToStageId(), template);
        if (from.getId().equals(to.getId())) {
            throw new InvalidWorkflowGraphException("A transition cannot start and end at the same stage");
        }
        if (transitionRepository.existsByTemplateIdAndFromStageIdAndToStageId(templateId, from.getId(), to.getId())) {
            throw new InvalidWorkflowGraphException(
                    "A transition from '" + from.getName() + "' to '" + to.getName() + "' already exists");
        }

        WorkflowTransition transition = WorkflowTransition.builder()
                .tenant(template.getTenant())
                .template(template)
                .fromStage(from)
                .toStage(to)
                .transitionType(request.getTransitionType() != null ? request.getTransitionType() : TransitionType.FORWARD)
                .label(request.getLabel())
                .requiresApproval(request.getRequiresApproval() != null && request.getRequiresApproval())
                .build();

        WorkflowTransition saved = transitionRepository.save(transition);
        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.WORKFLOW_TRANSITION_SAVED, "WORKFLOW_TRANSITION", saved.getId());
        return saved;
    }

    public void deleteTransition(UUID transitionId) {
        UUID tenantId = resolvedTenantId();
        WorkflowTransition transition = transitionRepository.findByIdAndTenantId(transitionId, tenantId)
                .orElseThrow(() -> new WorkflowTemplateNotFoundException("Transition not found: " + transitionId));
        transitionRepository.delete(transition);
        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.WORKFLOW_TRANSITION_DELETED, "WORKFLOW_TRANSITION", transitionId);
    }

    /**
     * Validates the full workflow graph (FR-4). Throws {@link InvalidWorkflowGraphException}
     * on the first problem found. Safe to call before publishing / assigning a workflow.
     */
    @Transactional(readOnly = true)
    public void validateGraph(UUID templateId) {
        UUID tenantId = resolvedTenantId();
        WorkflowTemplate template = findTemplateOrThrow(templateId, tenantId);
        List<WorkflowStage> stages = template.getStages();
        if (stages.isEmpty()) {
            throw new InvalidWorkflowGraphException("Workflow has no stages");
        }
        boolean hasFinal = stages.stream().anyMatch(s -> s.getStageType() == StageType.FINAL_STAGE);
        if (!hasFinal) {
            throw new InvalidWorkflowGraphException("Workflow must contain at least one FINAL_STAGE");
        }
        // Reachability from the entry stage (lowest display order) over all transitions.
        WorkflowStage entry = stages.stream()
                .min((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .orElseThrow();
        Map<UUID, List<UUID>> adj = new HashMap<>();
        for (WorkflowTransition t : template.getTransitions()) {
            adj.computeIfAbsent(t.getFromStage().getId(), _ -> new ArrayList<>()).add(t.getToStage().getId());
        }
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new java.util.ArrayDeque<>();
        queue.add(entry.getId());
        while (!queue.isEmpty()) {
            UUID cur = queue.poll();
            if (!visited.add(cur)) continue;
            for (UUID next : adj.getOrDefault(cur, List.of())) {
                if (!visited.contains(next)) queue.add(next);
            }
        }
        for (WorkflowStage s : stages) {
            if (!visited.contains(s.getId())) {
                throw new InvalidWorkflowGraphException(
                        "Stage '" + s.getName() + "' is unreachable from the entry stage");
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private WorkflowStage requireStageInTemplate(UUID stageId, WorkflowTemplate template) {
        return template.getStages().stream()
                .filter(s -> s.getId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new InvalidWorkflowGraphException(
                        "Stage " + stageId + " does not belong to this workflow"));
    }

    private void clearTenantDefaultIfMatches(UUID tenantId, UUID templateId) {
        tenantSettingsRepository.findByTenantId(tenantId).ifPresent(s -> {
            if (templateId.equals(s.getDefaultWorkflowTemplateId())) {
                s.setDefaultWorkflowTemplateId(null);
                tenantSettingsRepository.save(s);
            }
        });
    }

    private WorkflowTemplate findTemplateOrThrow(UUID templateId, UUID tenantId) {
        return templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new WorkflowTemplateNotFoundException(templateId));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private UUID resolvedTenantId() {
        return principal().getTenantId();
    }

    private UUID resolvedUserId() {
        return principal().getUserId();
    }

    private EduFlowUserDetails principal() {
        return (EduFlowUserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
