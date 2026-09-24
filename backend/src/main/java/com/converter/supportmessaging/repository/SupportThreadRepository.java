package com.converter.supportmessaging.repository;

import com.converter.supportmessaging.domain.SupportThread;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SupportThreadRepository extends JpaRepository<SupportThread, UUID> {

    Optional<SupportThread> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SupportThread t WHERE t.userId = :userId")
    Optional<SupportThread> findByUserIdForUpdate(@Param("userId") UUID userId);

    /** Fils les plus recemment actifs en premier — la vue "boite de reception" de l'admin. */
    Page<SupportThread> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
