package com.eduflow.student;

import com.eduflow.common.BaseEntity;
import com.eduflow.tenant.Tenant;
import com.eduflow.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A student (prospect or enrolled) belonging to a tenant consultancy.
 *
 * <p>Status transitions are enforced in {@code StudentService}:
 * {@code LEAD → QUALIFIED → ACTIVE → ENROLLED}; any status can move to {@code INACTIVE}.</p>
 *
 * <p>⚠️ Every query on this entity MUST filter by {@code tenantId}.</p>
 */
@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student extends BaseEntity {

    // ── Tenant scope ────────────────────────────────────────────────────────

    /** The consultancy this student belongs to. Never null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // ── Personal information ─────────────────────────────────────────────────

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Contact email. Unique within a tenant. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    /** Date of birth stored as {@link Instant}; treat as date-only (midnight UTC). */
    @Column(name = "date_of_birth")
    private Instant dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30)
    private Gender gender;

    @Column(name = "nationality", length = 100)
    private String nationality;

    // ── Address ──────────────────────────────────────────────────────────────

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private StudentStatus status = StudentStatus.LEAD;

    // ── Assigned staff ───────────────────────────────────────────────────────

    /** Counselor responsible for this student. Optional. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_counselor_id")
    private User assignedCounselor;

    // ── Interests ────────────────────────────────────────────────────────────

    /** Countries the student is interested in studying in. */
    @ElementCollection
    @CollectionTable(
            name = "student_interested_countries",
            joinColumns = @JoinColumn(name = "student_id")
    )
    @Column(name = "country", length = 100)
    @Builder.Default
    private List<String> interestedCountries = new ArrayList<>();

    /** Courses or fields the student is interested in. */
    @ElementCollection
    @CollectionTable(
            name = "student_interested_courses",
            joinColumns = @JoinColumn(name = "student_id")
    )
    @Column(name = "course", length = 255)
    @Builder.Default
    private List<String> interestedCourses = new ArrayList<>();

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Convenience accessor for the student's full display name. */
    public String getFullName() {
        return firstName + " " + lastName;
    }
}


