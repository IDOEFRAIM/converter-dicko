package com.converter.wallet.repository;

import com.converter.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    /**
     * Chargement verrouille ({@code SELECT ... FOR UPDATE}), requis
     * avant toute mutation de solde : deux operations concurrentes sur
     * le meme wallet (deux demandes de taux preferentiel, ou le
     * scheduler et une action utilisateur) se serialisent, la seconde
     * ne voit le solde disponible a jour qu'apres le commit de la
     * premiere -- meme principe que {@code TreasuryAccountRepository}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);
}
