package com.eduflow.tenant;

/**
 * Subscription tiers for a tenant. Each plan carries default resource limits that
 * seed the tenant's {@code maxStudents} / {@code maxStaffUsers} at creation; the
 * stored values may later be overridden per deal.
 *
 * <p>A {@code null} limit means unlimited (negotiated ENTERPRISE deals).</p>
 */
public enum TenantPlan {

    STARTER("Starter", 250, 5),
    PROFESSIONAL("Professional", 2_000, 25),
    ENTERPRISE("Enterprise", null, null);

    private final String label;
    private final Integer defaultMaxStudents;
    private final Integer defaultMaxStaffUsers;

    TenantPlan(String label, Integer defaultMaxStudents, Integer defaultMaxStaffUsers) {
        this.label = label;
        this.defaultMaxStudents = defaultMaxStudents;
        this.defaultMaxStaffUsers = defaultMaxStaffUsers;
    }

    public String label() {
        return label;
    }

    /** Default student cap for this plan; {@code null} = unlimited. */
    public Integer defaultMaxStudents() {
        return defaultMaxStudents;
    }

    /** Default staff-user cap for this plan; {@code null} = unlimited. */
    public Integer defaultMaxStaffUsers() {
        return defaultMaxStaffUsers;
    }
}
