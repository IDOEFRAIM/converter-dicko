package com.converter.wallet.domain;

import com.converter.common.domain.BaseEntity;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Solde interne XOF d'un utilisateur.
 *
 * <p>Le Wallet n'est PAS un compte bancaire : c'est un solde de
 * plateforme, sur le meme modele que {@code TreasuryAccount} --
 * {@code Available = balance - reserved_balance}. Toute mutation passe
 * par {@link com.converter.wallet.service.WalletService}, jamais par un
 * mutateur direct expose ailleurs : les quatre operations
 * (deposit/reserve/release/consume) sont les seules portes d'entree,
 * chacune ecrivant simultanement une ligne {@link WalletTransaction}
 * append-only.
 *
 * <p><b>Nature de cette entite (passe 2, §13)</b> : <b>projection materialisee</b> du ledger
 * append-only {@code wallet_transactions} (source de verite d'audit), maintenue
 * transactionnellement coherente avec lui — meme raisonnement que {@code TreasuryAccount}.
 */
@Entity
@Table(name = "wallets")
public class Wallet extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "reserved_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedBalance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Wallet() {
        // Requis par JPA.
    }

    public Wallet(UUID userId, Instant now) {
        this.userId = userId;
        this.balance = BigDecimal.ZERO.setScale(2);
        this.reservedBalance = BigDecimal.ZERO.setScale(2);
        this.updatedAt = now;
    }

    public BigDecimal available() {
        return balance.subtract(reservedBalance);
    }

    public void deposit(BigDecimal amount, Instant now) {
        this.balance = this.balance.add(amount);
        this.updatedAt = now;
    }

    public void reserve(BigDecimal amount, Instant now) {
        this.reservedBalance = this.reservedBalance.add(amount);
        this.updatedAt = now;
    }

    public void release(BigDecimal amount, Instant now) {
        requireReservedAtLeast(amount);
        this.reservedBalance = this.reservedBalance.subtract(amount);
        this.updatedAt = now;
    }

    /**
     * Consommation d'une reservation : le decaissement reel touche balance ET reserved_balance.
     *
     * <p>Garde d'invariant applicative, symetrique de {@code TreasuryAccount.consume} : un
     * utilisateur ne doit jamais pouvoir se voir debiter plus que ce qui a ete effectivement
     * reserve pour l'operation en cours -- defense en profondeur en complement des contraintes
     * SQL {@code ck_wallets_balance_non_negative}/{@code ck_wallets_reserved_non_negative}.
     */
    public void consume(BigDecimal amount, Instant now) {
        requireReservedAtLeast(amount);
        this.balance = this.balance.subtract(amount);
        this.reservedBalance = this.reservedBalance.subtract(amount);
        this.updatedAt = now;
    }

    private void requireReservedAtLeast(BigDecimal amount) {
        if (amount.compareTo(this.reservedBalance) > 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_WALLET_BALANCE,
                    "Incoherence de wallet : tentative de consommer/liberer " + amount
                            + " alors que seuls " + reservedBalance + " sont reserves.");
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getReservedBalance() {
        return reservedBalance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
