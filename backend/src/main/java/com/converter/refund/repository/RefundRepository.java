package com.converter.refund.repository;

import com.converter.refund.domain.Refund;
import com.converter.refund.domain.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    /**
     * Le remboursement "actif" (au plus un a la fois, voir {@code uq_refunds_payment_active},
     * migration V20) : {@code PENDING} ou {@code PROCESSED}. Un historique de tentatives
     * {@code REJECTED} n'est jamais actif — {@code findByPaymentId} etait ambigu des qu'un
     * paiement pouvait porter plusieurs lignes (mission "Refund retry policy").
     */
    Optional<Refund> findByPaymentIdAndStatusIn(UUID paymentId, List<RefundStatus> statuses);

    boolean existsByPaymentIdAndStatusIn(UUID paymentId, List<RefundStatus> statuses);

    boolean existsByOrderIdAndStatus(UUID orderId, RefundStatus status);

    /**
     * Le remboursement le plus recent d'un paiement, quel que soit son statut — contrairement a
     * {@link #findByPaymentIdAndStatusIn}, inclut deliberement {@code REJECTED} : destine au
     * justificatif de transaction (Phase 7), qui doit representer fidelement un remboursement
     * rejete plutot que de le faire disparaitre (voir {@code OrderReceiptService}).
     */
    Optional<Refund> findFirstByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") UUID id);
}
