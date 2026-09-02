package com.converter.quote.repository;

import com.converter.quote.domain.Quote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    /**
     * Chargement verrouille, requis avant toute transition
     * ({@code accept}/{@code cancel}) : deux tentatives concurrentes sur
     * le meme devis (double-clic, deux onglets) se serialisent, la
     * seconde constate le nouveau statut et echoue proprement plutot que
     * d'ecraser silencieusement la premiere.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quote q WHERE q.id = :id")
    Optional<Quote> findByIdForUpdate(@Param("id") UUID id);
}
