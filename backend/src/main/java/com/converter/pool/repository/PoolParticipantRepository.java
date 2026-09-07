package com.converter.pool.repository;

import com.converter.pool.domain.PoolParticipant;
import com.converter.pool.domain.PoolStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PoolParticipantRepository extends JpaRepository<PoolParticipant, UUID> {

    Optional<PoolParticipant> findByPoolIdAndUserId(UUID poolId, UUID userId);

    List<PoolParticipant> findByPoolIdOrderByJoinedAtAsc(UUID poolId);

    long countByPoolId(UUID poolId);

    @Query("SELECT p FROM PoolParticipant p WHERE p.userId = :userId ORDER BY p.joinedAt DESC")
    Page<PoolParticipant> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Une seule recompense active non consommee a la fois par utilisateur (verrouillee : voir
     * {@code QuoteService#create}, qui la consomme dans la meme transaction que le devis qu'elle
     * reduit -- deux creations de devis concurrentes pour le meme utilisateur ne doivent jamais
     * consommer deux fois la meme recompense).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM PoolParticipant p
            WHERE p.userId = :userId AND p.rewardGrantedAt IS NOT NULL AND p.rewardConsumedAt IS NULL
            ORDER BY p.rewardGrantedAt ASC
            """)
    List<PoolParticipant> findActiveUnconsumedRewardsForUpdate(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(p) FROM PoolParticipant p JOIN Pool pool ON pool.id = p.poolId
            WHERE p.userId = :userId AND pool.status = :status
            """)
    long countByUserIdAndPoolStatus(@Param("userId") UUID userId, @Param("status") PoolStatus status);
}
