package com.eduflow.user.dto;

/**
 * Aggregate metrics for the staff roster stat strip.
 *
 * @param total           total staff users in the tenant
 * @param activeCounselors active users holding {@code ROLE_COUNSELOR}
 * @param avgCaseload     average active caseload across active counselors
 * @param seatsUsed       staff seats consumed (== total)
 * @param seatsMax        the plan's staff cap, or {@code null} for unlimited
 * @param seatsAvailable  remaining seats, or {@code null} for unlimited
 * @param seatPct         percent of seats used (0 when unlimited)
 * @param planLabel       the tenant's plan name, for the seat card caption
 */
public record StaffStats(
        long total,
        long activeCounselors,
        long avgCaseload,
        long seatsUsed,
        Integer seatsMax,
        Integer seatsAvailable,
        int seatPct,
        String planLabel) {
}
