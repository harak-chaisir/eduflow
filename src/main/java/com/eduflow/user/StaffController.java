package com.eduflow.user;

import com.eduflow.user.dto.InviteStaffRequest;
import com.eduflow.user.dto.StaffInviteResult;
import com.eduflow.user.dto.StaffResponse;
import com.eduflow.user.dto.StaffSearchCriteria;
import com.eduflow.user.dto.UpdateStaffRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * REST controller for tenant staff management.
 *
 * <p>Base path: {@code /api/v1/staff}. All endpoints are tenant-scoped; the resolved
 * tenant ID is always taken from the authenticated principal in the service layer.
 * Restricted to tenant administrators (and platform super admins).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
public class StaffController {

    private final StaffService staffService;

    /** Returns a paginated, filtered list of staff for the authenticated user's tenant. */
    @GetMapping
    public ResponseEntity<Page<StaffResponse>> searchStaff(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String role,
            @PageableDefault(size = 20, sort = "firstName", direction = Sort.Direction.ASC)
            Pageable pageable) {

        StaffSearchCriteria criteria = StaffSearchCriteria.builder()
                .name(name).email(email).status(status).role(role).build();
        return ResponseEntity.ok(staffService.searchStaff(criteria, pageable));
    }

    /** Retrieves a single staff member by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<StaffResponse> getStaff(@PathVariable UUID id) {
        return ResponseEntity.ok(staffService.getStaff(id));
    }

    /** Invites a new staff member; returns the created record and set-password token. */
    @PostMapping
    public ResponseEntity<StaffInviteResult> inviteStaff(@Valid @RequestBody InviteStaffRequest request) {
        StaffInviteResult result = staffService.inviteStaff(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.staff().getId())
                .toUri();
        return ResponseEntity.created(location).body(result);
    }

    /** Updates a staff member's profile name and role assignment. */
    @PatchMapping("/{id}")
    public ResponseEntity<StaffResponse> updateStaff(
            @PathVariable UUID id, @Valid @RequestBody UpdateStaffRequest request) {
        return ResponseEntity.ok(staffService.updateStaff(id, request));
    }

    /** Activates or deactivates a staff member. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<StaffResponse> updateStatus(
            @PathVariable UUID id, @RequestParam UserStatus newStatus) {
        return ResponseEntity.ok(staffService.setStatus(id, newStatus));
    }

    /** Issues a fresh set-password token for a staff member. */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id) {
        staffService.issuePasswordReset(id);
        return ResponseEntity.noContent().build();
    }
}
