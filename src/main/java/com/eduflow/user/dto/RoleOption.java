package com.eduflow.user.dto;

/**
 * Lightweight option for the role checkboxes on the staff web form.
 *
 * @param name        the authority name, e.g. {@code ROLE_COUNSELOR}
 * @param label       a human-friendly label, e.g. {@code Counselor}
 * @param description optional role description for the form hint
 */
public record RoleOption(String name, String label, String description) {
}
