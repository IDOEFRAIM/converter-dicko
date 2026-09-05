package com.converter.treasury.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.converter.common.domain.BaseEntity;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Solde de tresorerie pour une devise.
 *
 * <p>{@code Available = balance - reserved_balance}. Toute mutation
 * passe par {@link com.converter.treasury.service.TreasuryService},
 * jamais par un mutateur direct expose ici : les cinq operations
 * (reserve/release/consume/deposit/adjust) sont les seules portes
 * d'entree, chacune ecrivant simultanement une ligne
 * {@link TreasuryTransaction} append-only.
 *
 * <p><b>Nature de cette entite (passe 2, §13)</b> : c'est une <b>projection materialisee</b> du
 * ledger append-only {@code treasury_transactions}, PAS la source de verite. La source de verite
 * d'audit est le ledger ; chaque ligne y porte {@code balance_after}/{@code reserved_after},
 * ce qui permet de reconstituer/reconcilier le solde sans rejouer tout l'historique. Le solde
 * materialise ici est la valeur de travail (verrous pessimistes {@code SELECT ... FOR UPDATE},
 * contraintes {@code CHECK}) et reste transactionnellement coherent avec le ledger : chaque
 * mutation de solde et l'ecriture de la ligne de ledger correspondante committent dans la
 * <b>meme transaction</b>.
 */
@Entity
@Table(name = "treasury_accounts")
public class TreasuryAccount extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "balance", nullable = false, precision = 21, scale = 2)
    private BigDecimal balance;

    @Column(name = "reserved_balance", nullable = false, precision = 21, scale = 2)
    private BigDecimal reservedBalance;

    @Column(name = "low_threshold", nullable = false, precision = 21, scale = 2)
    private BigDecimal lowThreshold;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TreasuryAccount() {
        // Requis par JPA.
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
     * <p><b>Garde d'invariant applicative</b>, en complement (jamais en remplacement) des
     * contraintes SQL {@code ck_treasury_accounts_reserved_positive} et
     * {@code ck_treasury_accounts_balance_positive} : {@code reserved_balance} est un solde
     * <em>agrege</em> par devise, partage entre tous les ordres. Consommer un montant superieur
     * a ce qui est reellement reserve ne doit jamais pouvoir "emprunter" silencieusement sur la
     * reservation d'un autre ordre, ni echouer tardivement avec une violation de contrainte SQL
     * peu explicite (traduite en {@code 409 DUPLICATE_RESOURCE} par {@code GlobalExceptionHandler},
     * un code qui ne dit rien de la vraie cause). L'appelant ({@link
     * com.converter.settlement.service.SettlementService}) doit par ailleurs ne jamais invoquer
     * cette methode pour un ordre qui n'a pas ete effectivement reserve.
     */
    public void consume(BigDecimal amount, Instant now) {
        requireReservedAtLeast(amount);
        this.balance = this.balance.subtract(amount);
        this.reservedBalance = this.reservedBalance.subtract(amount);
        this.updatedAt = now;
    }

    private void requireReservedAtLeast(BigDecimal amount) {
        if (amount.compareTo(this.reservedBalance) > 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_TREASURY,
                    "Incoherence de tresorerie " + currency + " : tentative de consommer/liberer "
                            + amount + " alors que seuls " + reservedBalance + " sont reserves.");
        }
    }

    /**
     * Decaissement direct, hors reservation (ex. remboursement client) : ne touche jamais
     * {@code reservedBalance}, contrairement a {@link #consume}. La garde compare au disponible
     * ({@code available()}), pas au seul {@code balance} : meme si XOF n'a aujourd'hui aucune
     * reservation active, ce decaissement ne doit jamais pouvoir faire passer {@code balance}
     * sous {@code reservedBalance} — invariant identique a celui deja applique dans {@code adjust()}.
     * Garde applicative explicite, en complement (jamais en remplacement) de
     * {@code ck_treasury_accounts_balance_positive} : un solde insuffisant doit produire une
     * erreur metier claire, pas une violation SQL opaque.
     */
    public void withdrawUnreserved(BigDecimal amount, Instant now) {
        if (amount.compareTo(this.available()) > 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_TREASURY,
                    "Solde " + currency + " insuffisant : disponible " + this.available() + ", demande " + amount + ".");
        }
        this.balance = this.balance.subtract(amount);
        this.updatedAt = now;
    }

    public void adjust(BigDecimal delta, Instant now) {
        this.balance = this.balance.add(delta);
        this.updatedAt = now;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getReservedBalance() {
        return reservedBalance;
    }

    public BigDecimal getLowThreshold() {
        return lowThreshold;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
