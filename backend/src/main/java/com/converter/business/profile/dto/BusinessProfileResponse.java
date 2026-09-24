package com.converter.business.profile.dto;

import com.converter.business.profile.domain.BusinessType;

import java.time.Instant;
import java.util.UUID;

public record BusinessProfileResponse(
        UUID id,
        String businessName,
        BusinessType businessType,
        String registrationNumber,
        String country,
        String city,
        String address,
        Instant createdAt,
        Instant updatedAt
) {
}
