package com.converter.treasury.repository;

import com.converter.treasury.domain.Currency;
import com.converter.treasury.domain.TreasuryAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TreasuryAccountRepository extends JpaRepository<TreasuryAccount, UUID> {

    Optional<TreasuryAccount> findByCurrency(Currency currency);

    /**
     * Chargement verrouille ({@code SELECT ... FOR UPDATE}), requis
     * avant toute mutation de solde : deux reservations concurrentes
     * sur la meme devise se serialisent, la seconde ne voit le solde
     * disponible a jour qu'apres le commit de la premiere — c'est ce
     * qui rend une sur-reservation physiquement impossible, en plus de
     * la contrainte CHECK {@code reserved_balance <= balance}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM TreasuryAccount a WHERE a.currency = :currency")
    Optional<TreasuryAccount> findByCurrencyForUpdate(@Param("currency") Currency currency);
}
