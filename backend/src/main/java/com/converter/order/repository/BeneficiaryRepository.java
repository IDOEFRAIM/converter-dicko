package com.converter.order.repository;

import com.converter.order.domain.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    Optional<Beneficiary> findByOrderId(UUID orderId);
}
