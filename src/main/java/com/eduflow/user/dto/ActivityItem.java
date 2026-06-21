package com.eduflow.user.dto;

import java.time.Instant;

/**
 * One entry in a staff member's recent-activity feed, derived from an audit event.
 *
 * @param label human-readable description of the action (e.g. "Registered a student")
 * @param icon  lucide icon name to show beside it
 * @param time  when it happened
 */
public record ActivityItem(String label, String icon, Instant time) {
}
