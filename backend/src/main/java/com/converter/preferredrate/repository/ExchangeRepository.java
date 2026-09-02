package com.converter.preferredrate.repository;

import com.converter.preferredrate.domain.Exchange;
import com.converter.preferredrate.domain.ExchangeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRepository extends JpaRepository<Exchange, UUID> {

    Optional<Exchange> findByPreferredRateRequestId(UUID preferredRateRequestId);

    @Query("SELECT e.id FROM Exchange e WHERE e.status = :status")
    List<UUID> findIdsByStatus(@Param("status") ExchangeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Exchange e WHERE e.id = :id")
    Optional<Exchange> findByIdForUpdate(@Param("id") UUID id);
}
