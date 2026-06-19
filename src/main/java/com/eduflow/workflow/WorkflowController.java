package com.eduflow.workflow;

import com.eduflow.workflow.dto.WorkflowSearchCriteria;
import com.eduflow.workflow.dto.WorkflowStageRequest;
import com.eduflow.workflow.dto.WorkflowStageResponse;
import com.eduflow.workflow.dto.WorkflowTemplateRequest;
import com.eduflow.workflow.dto.WorkflowTemplateResponse;
import com.eduflow.workflow.dto.WorkflowTransitionRequest;
import com.eduflow.workflow.dto.WorkflowTransitionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * REST API for the Workflow Management module (PRD §17 FR-1..FR-5, FR-9).
 *
 * <p>Base path: {@code /api/v1/workflows}. All endpoints are tenant-scoped via the
 * authenticated principal and restricted to tenant administrators.</p>
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
public class WorkflowController {

    private final WorkflowTemplateService workflowService;

    // ── Templates ────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Page<WorkflowTemplateResponse>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean archived,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        WorkflowSearchCriteria criteria = WorkflowSearchCriteria.builder()
                .name(name).country(country).active(active).archived(archived).build();
        return ResponseEntity.ok(workflowService.searchAsResponses(criteria, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowTemplateResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(WorkflowTemplateResponse.withGraph(workflowService.getWithGraph(id)));
    }

    @PostMapping
    public ResponseEntity<WorkflowTemplateResponse> create(@Valid @RequestBody WorkflowTemplateRequest request) {
        WorkflowTemplateResponse response = WorkflowTemplateResponse.from(workflowService.create(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkflowTemplateResponse> update(
            @PathVariable UUID id, @Valid @RequestBody WorkflowTemplateRequest request) {
        return ResponseEntity.ok(WorkflowTemplateResponse.from(workflowService.update(id, request)));
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<WorkflowTemplateResponse> clone(@PathVariable UUID id) {
        return ResponseEntity.ok(WorkflowTemplateResponse.from(workflowService.clone(id)));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        workflowService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        workflowService.archive(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<Void> setDefault(@PathVariable UUID id) {
        workflowService.setDefault(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<Void> validate(@PathVariable UUID id) {
        workflowService.validateGraph(id);
        return ResponseEntity.noContent().build();
    }

    // ── Stages ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/stages")
    public ResponseEntity<WorkflowStageResponse> addStage(
            @PathVariable UUID id, @Valid @RequestBody WorkflowStageRequest request) {
        return ResponseEntity.ok(WorkflowStageResponse.from(workflowService.addStage(id, request)));
    }

    @PutMapping("/stages/{stageId}")
    public ResponseEntity<WorkflowStageResponse> updateStage(
            @PathVariable UUID stageId, @Valid @RequestBody WorkflowStageRequest request) {
        return ResponseEntity.ok(WorkflowStageResponse.from(workflowService.updateStage(stageId, request)));
    }

    @DeleteMapping("/stages/{stageId}")
    public ResponseEntity<Void> deleteStage(@PathVariable UUID stageId) {
        workflowService.deleteStage(stageId);
        return ResponseEntity.noContent().build();
    }

    // ── Transitions ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/transitions")
    public ResponseEntity<WorkflowTransitionResponse> addTransition(
            @PathVariable UUID id, @Valid @RequestBody WorkflowTransitionRequest request) {
        return ResponseEntity.ok(WorkflowTransitionResponse.from(workflowService.addTransition(id, request)));
    }

    @DeleteMapping("/transitions/{transitionId}")
    public ResponseEntity<Void> deleteTransition(@PathVariable UUID transitionId) {
        workflowService.deleteTransition(transitionId);
        return ResponseEntity.noContent().build();
    }
}
