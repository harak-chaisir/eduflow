package com.eduflow.university.dto;

import com.eduflow.university.University;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable response representation of a {@link University}.
 */
@Value
@Builder
public class UniversityResponse {

    UUID id;
    String name;
    String country;
    String city;
    String website;
    String code;
    boolean active;

    UUID tenantId;

    Instant createdAt;
    Instant updatedAt;
    String createdBy;

    public static UniversityResponse from(University u) {
        return UniversityResponse.builder()
                .id(u.getId())
                .name(u.getName())
                .country(u.getCountry())
                .city(u.getCity())
                .website(u.getWebsite())
                .code(u.getCode())
                .active(u.isActive())
                .tenantId(u.getTenant().getId())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .createdBy(u.getCreatedBy())
                .build();
    }
}
