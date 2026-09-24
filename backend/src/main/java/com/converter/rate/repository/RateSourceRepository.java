package com.converter.rate.repository;

import com.converter.rate.domain.RateProviderType;
import com.converter.rate.domain.RateSource;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RateSourceRepository extends JpaRepository<RateSource, UUID> {

    /**
     * Lecture non verrouillee de la cotation courante — pour un simple
     * affichage administratif, pas pour une decision financiere.
     */
    Optional<RateSource> findByProviderTypeAndCurrencyPairAndEffectiveToIsNull(
            RateProviderType providerType, String currencyPair);

    /**
     * Lecture verrouillee ({@code SELECT ... FOR SHARE}) de la cotation
     * courante, a utiliser par tout calcul qui va figer un montant
     * financier (creation d'un {@code Quote}).
     *
     * <p>Un verrou partage permet a plusieurs devis de se calculer
     * simultanement (les lecteurs ne se bloquent pas entre eux), mais
     * bloque une publication concurrente ({@link #closeCurrent}, qui
     * necessite un verrou exclusif sur la meme ligne) jusqu'a ce que la
     * transaction de calcul du devis se termine. Cela garantit qu'un
     * {@code Quote} ne peut jamais figer un taux qui vient d'etre
     * remplace au meme instant — voir docs/ARCHITECTURE.md, Partie I,
     * section G.6 / S (risque 29).
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT r FROM RateSource r
            WHERE r.providerType = :providerType
              AND r.currencyPair = :currencyPair
              AND r.effectiveTo IS NULL
            """)
    Optional<RateSource> findCurrentForPricing(@Param("providerType") RateProviderType providerType,
                                               @Param("currencyPair") String currencyPair);

    /**
     * Cloture la cotation courante, s'il en existe une.
     *
     * <p>Requete de mise a jour en masse (JPQL {@code UPDATE}) plutot
     * que chargement + mutation d'entite : {@link RateSource} est
     * annotee {@code @Immutable} cote Hibernate (aucune ligne publiee
     * n'est jamais modifiee par le flux applicatif normal), cette seule
     * operation de cloture passe donc explicitement par une requete
     * native au niveau SQL.
     */
    @Modifying
    @Query("""
            UPDATE RateSource r
            SET r.effectiveTo = :now
            WHERE r.providerType = :providerType
              AND r.currencyPair = :currencyPair
              AND r.effectiveTo IS NULL
            """)
    int closeCurrent(@Param("providerType") RateProviderType providerType,
                     @Param("currencyPair") String currencyPair,
                     @Param("now") Instant now);

    Page<RateSource> findByProviderTypeAndCurrencyPairOrderByEffectiveFromDesc(
            RateProviderType providerType, String currencyPair, Pageable pageable);
}
