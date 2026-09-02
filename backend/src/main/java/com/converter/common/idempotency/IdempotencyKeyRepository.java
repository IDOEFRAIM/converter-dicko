package com.converter.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
