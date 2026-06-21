package com.eduflow.user.dto;

import com.eduflow.student.dto.StudentResponse;

import java.util.List;

/**
 * A counselor's active caseload for the staff detail page.
 *
 * @param hasCaseload whether this staff member owns a caseload (false for non-counselors
 *                    or counselors with zero active students)
 * @param count       total active students assigned
 * @param pipeline    per-status breakdown, in lifecycle order
 * @param students    the assigned students (caller may render a capped subset)
 */
public record CaseloadView(
        boolean hasCaseload,
        long count,
        List<PipelineStage> pipeline,
        List<StudentResponse> students) {

    /** An empty caseload (non-counselor, or counselor with no active students). */
    public static CaseloadView empty() {
        return new CaseloadView(false, 0, List.of(), List.of());
    }
}
