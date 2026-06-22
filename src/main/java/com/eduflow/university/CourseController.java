package com.eduflow.university;

import com.eduflow.university.dto.CourseRequest;
import com.eduflow.university.dto.CourseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for individual {@link Course} operations not nested under a
 * university. Base path: {@code /api/v1/courses}.
 */
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<CourseResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(courseService.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<CourseResponse> update(
            @PathVariable UUID id, @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(courseService.update(id, request));
    }
}
