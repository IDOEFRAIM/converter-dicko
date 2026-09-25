package com.converter.transfi.repository;

import com.converter.transfi.domain.TransfiOrder;
import com.converter.transfi.domain.TransfiOrderDirection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransfiOrderRepository extends JpaRepository<TransfiOrder, UUID> {

    Optional<TransfiOrder> findByOrderIdAndDirection(UUID orderId, TransfiOrderDirection direction);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransfiOrder t WHERE t.orderId = :orderId AND t.direction = :direction")
    Optional<TransfiOrder> findByOrderIdAndDirectionForUpdate(@Param("orderId") UUID orderId,
                                                               @Param("direction") TransfiOrderDirection direction);

    Optional<TransfiOrder> findByProviderOrderId(String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransfiOrder t WHERE t.providerOrderId = :providerOrderId")
    Optional<TransfiOrder> findByProviderOrderIdForUpdate(@Param("providerOrderId") String providerOrderId);

    Page<TransfiOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
