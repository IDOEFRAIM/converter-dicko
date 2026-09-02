package com.converter.payment.repository;

import com.converter.payment.domain.Payment;
import com.converter.payment.domain.PaymentMethod;
import com.converter.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    /** Pre-verification pour un message d'erreur precis avant de heurter {@code uq_payments_txref}. */
    boolean existsByMethodAndTransactionReference(PaymentMethod method, String transactionReference);

    Page<Payment> findByStatusOrderBySubmittedAtAsc(PaymentStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);
}
