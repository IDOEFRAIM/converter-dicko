package com.converter.preferredrate.repository;

import com.converter.preferredrate.domain.PreferredRateRequest;
import com.converter.preferredrate.domain.PreferredRateStatus;
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

public interface PreferredRateRequestRepository extends JpaRepository<PreferredRateRequest, UUID> {

    Page<PreferredRateRequest> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Chargement verrouille, requis avant toute transition
     * ({@code execute}/{@code expire}/{@code cancel}) : le scheduler et
     * une action utilisateur (annulation) qui visent la meme demande au
     * meme instant se serialisent -- la seconde constate le nouveau
     * statut et n'a plus rien a faire, plutot que d'ecraser
     * silencieusement la premiere transition.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PreferredRateRequest p WHERE p.id = :id")
    Optional<PreferredRateRequest> findByIdForUpdate(@Param("id") UUID id);

    /** Candidats a evaluer par le scheduler : uniquement les identifiants, le verrou est pris ligne par ligne ensuite. */
    @Query("SELECT p.id FROM PreferredRateRequest p WHERE p.status = :status")
    List<UUID> findIdsByStatus(@Param("status") PreferredRateStatus status);
}
