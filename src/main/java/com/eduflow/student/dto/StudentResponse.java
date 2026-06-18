package com.eduflow.student.dto;

import com.eduflow.student.Gender;
import com.eduflow.student.Student;
import com.eduflow.student.StudentStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable response representation of a {@link Student}.
 *
 * <p>Never exposes credentials, Drive tokens, or any internal implementation detail.</p>
 */
@Value
@Builder
public class StudentResponse {

    UUID id;

    // ── Personal ─────────────────────────────────────────────────────────────
    String firstName;
    String lastName;
    String fullName;
    String email;
    String phone;
    Instant dateOfBirth;
    Gender gender;
    String nationality;

    // ── Address ──────────────────────────────────────────────────────────────
    String addressLine1;
    String addressLine2;
    String city;
    String stateProvince;
    String country;
    String postalCode;

    // ── Lifecycle ────────────────────────────────────────────────────────────
    StudentStatus status;

    // ── Interests ────────────────────────────────────────────────────────────
    List<String> interestedCountries;
    List<String> interestedCourses;

    // ── Assignment ───────────────────────────────────────────────────────────
    UUID assignedCounselorId;
    String assignedCounselorName;

    // ── Tenant ───────────────────────────────────────────────────────────────
    UUID tenantId;

    // ── Audit ────────────────────────────────────────────────────────────────
    Instant createdAt;
    Instant updatedAt;
    String createdBy;

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Maps a {@link Student} entity to a {@link StudentResponse}.
     *
     * @param student the entity to map
     * @return a populated response DTO
     */
    public static StudentResponse from(Student student) {
        return StudentResponse.builder()
                .id(student.getId())
                .firstName(student.getFirstName())
                .lastName(student.getLastName())
                .fullName(student.getFullName())
                .email(student.getEmail())
                .phone(student.getPhone())
                .dateOfBirth(student.getDateOfBirth())
                .gender(student.getGender())
                .nationality(student.getNationality())
                .addressLine1(student.getAddressLine1())
                .addressLine2(student.getAddressLine2())
                .city(student.getCity())
                .stateProvince(student.getStateProvince())
                .country(student.getCountry())
                .postalCode(student.getPostalCode())
                .status(student.getStatus())
                .interestedCountries(student.getInterestedCountries() != null
                        ? new java.util.ArrayList<>(student.getInterestedCountries())
                        : new java.util.ArrayList<>())
                .interestedCourses(student.getInterestedCourses() != null
                        ? new java.util.ArrayList<>(student.getInterestedCourses())
                        : new java.util.ArrayList<>())
                .assignedCounselorId(
                        student.getAssignedCounselor() != null
                                ? student.getAssignedCounselor().getId()
                                : null)
                .assignedCounselorName(
                        student.getAssignedCounselor() != null
                                ? student.getAssignedCounselor().getFullName()
                                : null)
                .tenantId(student.getTenant().getId())
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .createdBy(student.getCreatedBy())
                .build();
    }
}

