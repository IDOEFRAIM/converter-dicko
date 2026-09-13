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

    /**
     * Meme pre-verification, en excluant le paiement en cours de resoumission lui-meme : sinon
     * un client qui resoumet avec exactement la MEME reference de transaction (le paiement reel
     * hors plateforme n'a pas change) se verrait refuser sa propre reference existante.
     */
    boolean existsByMethodAndTransactionReferenceAndIdNot(PaymentMethod method, String transactionReference, UUID id);

    Page<Payment> findByStatusOrderBySubmittedAtAsc(PaymentStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
