package com.converter.business.profile.repository;

import com.converter.business.profile.domain.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {

    Optional<BusinessProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
