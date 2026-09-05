package com.converter.order.repository;

import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.supplier.domain.Purpose;
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

    /**
     * Historique enrichi (Phase 8, {@code GET /api/v1/orders/history}) : filtres tous optionnels
     * (paires {@code hasXxx}/{@code xxx}, meme patron que {@code PublicRateSnapshotRepository} —
     * evite le bug JDBC "could not determine data type of parameter" d'un {@code :x IS NULL} sans
     * autre occurrence). {@code userId} n'est en revanche jamais optionnel : cette methode ne
     * renvoie jamais que les ordres de l'appelant, quels que soient les autres filtres — un
     * {@code supplierId} appartenant a un autre utilisateur ne sort donc jamais du perimetre,
     * il ne produit simplement aucun resultat. Tri fixe {@code createdAt DESC, id DESC},
     * non negociable par l'appelant (voir {@code OrderHistoryService}).
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.userId = :userId
              AND (:hasStatus = false OR o.status = :status)
              AND (:hasPurpose = false OR o.purpose = :purpose)
              AND (:hasSupplierId = false OR o.supplierId = :supplierId)
              AND (:hasFrom = false OR o.createdAt >= :from)
              AND (:hasTo = false OR o.createdAt < :to)
            ORDER BY o.createdAt DESC, o.id DESC
            """)
    Page<Order> searchHistory(@Param("userId") UUID userId,
                              @Param("hasStatus") boolean hasStatus, @Param("status") OrderStatus status,
                              @Param("hasPurpose") boolean hasPurpose, @Param("purpose") Purpose purpose,
                              @Param("hasSupplierId") boolean hasSupplierId, @Param("supplierId") UUID supplierId,
                              @Param("hasFrom") boolean hasFrom, @Param("from") Instant from,
                              @Param("hasTo") boolean hasTo, @Param("to") Instant to,
                              Pageable pageable);

    /**
     * Agregation SQL par statut (Phase 8, {@code BusinessPaymentReportService}) — jamais un
     * chargement de tous les ordres suivi d'une somme cote Java. Meme convention de plage que
     * {@link #searchHistory} : {@code from <= createdAt < to}.
     */
    @Query("""
            SELECT new com.converter.order.repository.OrderStatusAggregate(
                o.status, COUNT(o), COALESCE(SUM(o.amountXof), 0), COALESCE(SUM(o.amountCny), 0),
                COALESCE(SUM(o.feeXof), 0))
            FROM Order o
            WHERE o.userId = :userId
              AND (:hasFrom = false OR o.createdAt >= :from)
              AND (:hasTo = false OR o.createdAt < :to)
            GROUP BY o.status
            """)
    List<OrderStatusAggregate> aggregateByStatus(@Param("userId") UUID userId,
                                                 @Param("hasFrom") boolean hasFrom, @Param("from") Instant from,
                                                 @Param("hasTo") boolean hasTo, @Param("to") Instant to);
}
