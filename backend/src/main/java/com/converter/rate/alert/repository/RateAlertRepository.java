package com.converter.rate.alert.repository;

import com.converter.rate.alert.domain.RateAlert;
import com.converter.rate.alert.domain.RateAlertStatus;
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

public interface RateAlertRepository extends JpaRepository<RateAlert, UUID> {

    Page<RateAlert> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<RateAlert> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, RateAlertStatus status, Pageable pageable);

    /**
     * Chargement verrouille, requis avant toute transition ({@code trigger}/{@code expire}/
     * {@code cancel}) : le scheduler et une action utilisateur (annulation) qui visent la meme
     * alerte au meme instant se serialisent — la seconde constate le nouveau statut et n'a plus
     * rien a faire (double-check dans {@code RateAlertService#processOne}), plutot que d'ecraser
     * silencieusement la premiere transition ou d'envoyer une seconde notification. Meme patron
     * que {@code PreferredRateRequestRepository#findByIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM RateAlert a WHERE a.id = :id")
    Optional<RateAlert> findByIdForUpdate(@Param("id") UUID id);

    /** Candidats a evaluer par le scheduler : uniquement les identifiants, le verrou est pris ligne par ligne ensuite. */
    @Query("SELECT a.id FROM RateAlert a WHERE a.status = :status")
    List<UUID> findIdsByStatus(@Param("status") RateAlertStatus status);
}
