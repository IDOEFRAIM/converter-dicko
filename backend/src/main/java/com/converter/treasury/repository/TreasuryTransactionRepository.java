package com.converter.treasury.repository;

import com.converter.treasury.domain.TreasuryTransaction;
import com.converter.treasury.domain.TreasuryTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TreasuryTransactionRepository extends JpaRepository<TreasuryTransaction, UUID> {

    Page<TreasuryTransaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    List<TreasuryTransaction> findByOrderId(UUID orderId);

    @Query("""
            SELECT COUNT(t) FROM TreasuryTransaction t
            WHERE t.orderId = :orderId AND t.type = :type
            """)
    long countByOrderIdAndType(@Param("orderId") UUID orderId, @Param("type") TreasuryTransactionType type);
}
