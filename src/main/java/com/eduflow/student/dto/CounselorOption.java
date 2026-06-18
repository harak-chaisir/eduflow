package com.eduflow.student.dto;

import java.util.UUID;

/**
 * Lightweight option for the counselor picker on the student web form.
 * Carries only the id and display name — never any credential or sensitive field.
 *
 * @param id   the counselor user's UUID
 * @param name the counselor's full display name
 */
public record CounselorOption(UUID id, String name) {
}
