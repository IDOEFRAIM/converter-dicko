package com.converter.rate.publicrate.repository;

import com.converter.rate.publicrate.domain.PublicRateSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PublicRateSnapshotRepository extends JpaRepository<PublicRateSnapshot, UUID> {

    /**
     * {@code from}/{@code to} optionnels (bornes nullables, {@code hasFrom}/{@code hasTo}
     * indiquent explicitement leur presence). Tri fixe {@code recordedAt DESC, id DESC} — le
     * plus recent en premier, avec {@code id} comme tie-breaker deterministe en cas d'egalite
     * exacte de {@code recordedAt} (voir {@code PublicRateSnapshotService}, qui ignore
     * volontairement tout tri porte par le {@code Pageable} appelant : ce contrat n'est jamais
     * negociable par le client).
     *
     * <p><b>Pourquoi {@code hasFrom}/{@code hasTo} plutot que {@code :from IS NULL}</b> : PostgreSQL
     * ne peut pas inferer le type d'un parametre lie dont la <em>seule</em> occurrence dans la
     * requete est un test {@code IS NULL} (aucun contexte de type) — erreur JDBC "could not
     * determine data type of parameter" a la preparation. En donnant a {@code :from}/{@code :to}
     * une occurrence non ambigue ({@code s.recordedAt >= :from}), leur type est toujours
     * inferable, meme quand la branche est logiquement ignoree a l'execution.
     */
    @Query("SELECT s FROM PublicRateSnapshot s WHERE s.currencyPair = :pair "
            + "AND (:hasFrom = false OR s.recordedAt >= :from) "
            + "AND (:hasTo = false OR s.recordedAt <= :to) "
            + "ORDER BY s.recordedAt DESC, s.id DESC")
    Page<PublicRateSnapshot> search(@Param("pair") String pair,
                                    @Param("hasFrom") boolean hasFrom, @Param("from") Instant from,
                                    @Param("hasTo") boolean hasTo, @Param("to") Instant to,
                                    Pageable pageable);

    /**
     * Le tout dernier snapshot d'une paire, meme ordre deterministe que {@link #search}
     * ({@code recordedAt DESC, id DESC}). Ajoute pour {@code RateAlertService} (Phase 6) — evite
     * de faire porter une pagination complete a un appelant qui n'a besoin que d'une seule ligne.
     */
    Optional<PublicRateSnapshot> findFirstByCurrencyPairOrderByRecordedAtDescIdDesc(String currencyPair);
}
