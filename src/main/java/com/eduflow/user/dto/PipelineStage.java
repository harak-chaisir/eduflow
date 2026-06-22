package com.eduflow.user.dto;

import com.eduflow.student.StudentStatus;

/**
 * One bucket of a counselor's caseload pipeline: a student status and how many of their
 * students are in it.
 *
 * @param status the student lifecycle status
 * @param count  number of the counselor's students in this status
 */
public record PipelineStage(StudentStatus status, long count) {
}
