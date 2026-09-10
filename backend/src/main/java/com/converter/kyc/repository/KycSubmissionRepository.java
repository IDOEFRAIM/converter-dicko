package com.converter.kyc.repository;

import com.converter.kyc.domain.KycSubmission;
import com.converter.kyc.domain.KycSubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycSubmissionRepository extends JpaRepository<KycSubmission, UUID> {

    Optional<KycSubmission> findFirstByUserIdOrderBySubmittedAtDesc(UUID userId);

    boolean existsByUserIdAndStatus(UUID userId, KycSubmissionStatus status);

    Page<KycSubmission> findByStatusOrderBySubmittedAtAsc(KycSubmissionStatus status, Pageable pageable);
}
