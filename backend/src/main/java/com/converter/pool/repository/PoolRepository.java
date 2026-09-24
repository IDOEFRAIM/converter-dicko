package com.converter.pool.repository;

import com.converter.pool.domain.Pool;
import com.converter.pool.domain.PoolStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PoolRepository extends JpaRepository<Pool, UUID> {

    Optional<Pool> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Chargement verrouille avant toute transition ({@code contribute}/{@code succeed}/{@code
     * expire}/{@code cancel}) -- une contribution (creation d'ordre) et le scheduler d'expiration
     * visant le meme pool au meme instant se serialisent, meme patron que {@code
     * RateAlertRepository#findByIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pool p WHERE p.id = :id")
    Optional<Pool> findByIdForUpdate(@Param("id") UUID id);

    /** Candidats a evaluer par le scheduler d'expiration : uniquement les identifiants. */
    @Query("SELECT p.id FROM Pool p WHERE p.status = :status")
    List<UUID> findIdsByStatus(@Param("status") PoolStatus status);
}
