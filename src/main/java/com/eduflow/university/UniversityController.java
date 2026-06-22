package com.eduflow.university;

import com.eduflow.university.dto.CourseRequest;
import com.eduflow.university.dto.CourseResponse;
import com.eduflow.university.dto.UniversityRequest;
import com.eduflow.university.dto.UniversityResponse;
import com.eduflow.university.dto.UniversitySearchCriteria;
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
import java.util.List;
import java.util.UUID;

/**
 * REST controller for university master data and its nested courses.
 *
 * <p>Base path: {@code /api/v1/universities}. All endpoints are tenant-scoped; the
 * tenant is resolved from the authenticated principal in the service layer.</p>
 */
@RestController
@RequestMapping("/api/v1/universities")
@RequiredArgsConstructor
public class UniversityController {

    private final UniversityService universityService;
    private final CourseService courseService;

    // ── Universities ──────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<Page<UniversityResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        UniversitySearchCriteria criteria = UniversitySearchCriteria.builder()
                .q(q).country(country).active(active).build();
        return ResponseEntity.ok(universityService.search(criteria, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<UniversityResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(universityService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<UniversityResponse> create(@Valid @RequestBody UniversityRequest request) {
        UniversityResponse response = universityService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<UniversityResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UniversityRequest request) {
        return ResponseEntity.ok(universityService.update(id, request));
    }

    // ── Courses nested under a university ─────────────────────────────────────

    @GetMapping("/{id}/courses")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<List<CourseResponse>> listCourses(@PathVariable UUID id) {
        return ResponseEntity.ok(courseService.listForUniversity(id));
    }

    @PostMapping("/{id}/courses")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<CourseResponse> createCourse(
            @PathVariable UUID id, @Valid @RequestBody CourseRequest request) {
        CourseResponse response = courseService.create(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/courses/{cid}").buildAndExpand(response.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }
}
