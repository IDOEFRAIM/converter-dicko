package com.converter.order.repository;

import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByQuoteId(UUID quoteId);

    /**
     * Identifiants des ordres encore en attente de paiement dont l'echeance est atteinte —
     * candidats a l'expiration. Ne renvoie que les identifiants : le verrou pessimiste est
     * ensuite pris ligne par ligne par {@code OrderService.expireIfOverdue}, avec re-verification
     * du statut et de l'echeance (patron identique a {@code PreferredRateService}).
     */
    @Query("""
            SELECT o.id FROM Order o
            WHERE o.status = com.converter.order.domain.OrderStatus.AWAITING_PAYMENT
              AND o.paymentDeadlineAt <= :asOf
            """)
    List<UUID> findOverdueAwaitingPaymentIds(@Param("asOf") Instant asOf);

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    /**
     * Chargement verrouille, requis avant toute transition : deux
     * administrateurs (ou un double-clic client) declenchant la meme
     * action simultanement se serialisent.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    /** Genere la reference lisible (sequence PostgreSQL, migration V6). */
    @Query(value = "SELECT next_order_reference()", nativeQuery = true)
    String nextReference();

    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.userId = :userId
              AND o.status IN ('AWAITING_PAYMENT', 'PAYMENT_SUBMITTED', 'PAYMENT_VERIFIED', 'PROCESSING')
            """)
    long countOpenOrdersByUser(@Param("userId") UUID userId);
}
