package com.converter.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByUserIdAndEndpointAndIdemKey(UUID userId, String endpoint, String idemKey);

    /**
     * Supprime la capture UNIQUEMENT si elle est encore en attente ({@code response_status IS NULL}).
     *
     * <p>Utilisee lorsque l'operation metier associee a echoue : une capture jamais completee ne
     * doit pas bloquer indefiniment une nouvelle tentative legitime avec la meme cle (ex. le
     * client corrige son erreur puis rejoue). Le filtre {@code response_status IS NULL} garantit
     * qu'une capture deja completee — la seule source de verite pour un rejeu — n'est jamais
     * supprimee par erreur.
     */
    @Modifying
    @Query("""
            DELETE FROM IdempotencyKey k
            WHERE k.userId = :userId AND k.endpoint = :endpoint AND k.idemKey = :idemKey
              AND k.responseStatus IS NULL
            """)
    int deletePending(@Param("userId") UUID userId, @Param("endpoint") String endpoint, @Param("idemKey") String idemKey);

    /**
     * Recupere les captures "en attente" (aucune reponse jamais enregistree) plus vieilles que
     * {@code threshold} — voir {@link IdempotencyService#reclaimStalePending} pour la preuve que
     * cette suppression ne peut jamais toucher une operation metier reellement committee.
     *
     * <p>Filtre explicitement sur {@code response_status IS NULL}, exactement comme
     * {@link #deletePending} : une capture deja completee (rejeu possible) n'est jamais une
     * candidate, quel que soit son age.
     */
    @Modifying
    @Query("""
            DELETE FROM IdempotencyKey k
            WHERE k.responseStatus IS NULL AND k.createdAt < :threshold
            """)
    int deleteStalePendingOlderThan(@Param("threshold") Instant threshold);
}
