package com.converter.payment.repository;

import com.converter.payment.domain.PaymentProof;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentProofRepository extends JpaRepository<PaymentProof, UUID> {

    List<PaymentProof> findByPaymentIdOrderByUploadedAtAsc(UUID paymentId);

    long countByPaymentId(UUID paymentId);
}
