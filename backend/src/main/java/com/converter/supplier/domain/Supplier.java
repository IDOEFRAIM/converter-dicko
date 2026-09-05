package com.converter.supplier.domain;

import com.converter.common.domain.AuditableEntity;
import com.converter.order.domain.BeneficiaryType;
import com.converter.treasury.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Carnet de fournisseurs/beneficiaires reutilisables d'un client — <b>distinct</b> du snapshot
 * immuable {@link com.converter.order.domain.Beneficiary} lie a un {@code Order}.
 *
 * <p>Un {@code Supplier} n'est jamais la source de verite d'un reglement deja effectue : a la
 * creation d'un ordre, ses champs pertinents sont copies dans un nouveau
 * {@code Beneficiary} immuable ; une modification ulterieure de ce {@code Supplier} n'a donc
 * structurellement aucun moyen d'alterer l'historique financier (voir Phase 2, {@code
 * orders.supplier_id}, une reference purement tracable, jamais une source de donnees a la
 * lecture d'un ordre existant).
 *
 * <p>Isolation stricte par {@code ownerUserId} : verifiee par {@link
 * com.converter.security.OwnershipService} a chaque acces, jamais uniquement au niveau SQL.
 *
 * <p>{@code type} reutilise {@link BeneficiaryType} (pas un enum parallele) : le mapping vers le
 * snapshot de commande reste une simple copie de valeur, jamais une table de correspondance a
 * maintenir.
 */
@Entity
@Table(name = "suppliers")
public class Supplier extends AuditableEntity {

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private BeneficiaryType type;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "legal_name", length = 160)
    private String legalName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_branch", length = 120)
    private String bankBranch;

    @Column(name = "account_name", length = 120)
    private String accountName;

    /** Compte Alipay / WeChat ou numero de compte bancaire — equivalent de {@code Beneficiary.identifier}. */
    @Column(name = "account_number", nullable = false, length = 120)
    private String accountNumber;

    @Column(name = "bank_address", length = 255)
    private String bankAddress;

    @Column(name = "swift_code", length = 20)
    private String swiftCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    /** Classification par defaut, jamais copiee aveuglement sur un ordre — voir {@link Purpose}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 24)
    private Purpose purpose;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SupplierStatus status;

    protected Supplier() {
        // Requis par JPA.
    }

    public Supplier(UUID ownerUserId, BeneficiaryType type, String displayName, String legalName,
                    String phone, String email, String country, String city, String province,
                    String bankName, String bankBranch, String accountName, String accountNumber,
                    String bankAddress, String swiftCode, Currency currency, Purpose purpose,
                    String notes) {
        requireBankNameForBankAccount(type, bankName);
        this.ownerUserId = ownerUserId;
        this.type = type;
        this.displayName = displayName;
        this.legalName = legalName;
        this.phone = phone;
        this.email = email;
        this.country = country;
        this.city = city;
        this.province = province;
        this.bankName = bankName;
        this.bankBranch = bankBranch;
        this.accountName = accountName;
        this.accountNumber = accountNumber;
        this.bankAddress = bankAddress;
        this.swiftCode = swiftCode;
        this.currency = currency;
        this.purpose = purpose;
        this.notes = notes;
        this.favorite = false;
        this.status = SupplierStatus.ACTIVE;
    }

    /** Met a jour les champs modifiables. N'affecte jamais un {@code Beneficiary} deja cree a partir de ce fournisseur. */
    public void update(BeneficiaryType type, String displayName, String legalName, String phone, String email,
                       String country, String city, String province, String bankName, String bankBranch,
                       String accountName, String accountNumber, String bankAddress, String swiftCode,
                       Currency currency, Purpose purpose, String notes) {
        requireBankNameForBankAccount(type, bankName);
        this.type = type;
        this.displayName = displayName;
        this.legalName = legalName;
        this.phone = phone;
        this.email = email;
        this.country = country;
        this.city = city;
        this.province = province;
        this.bankName = bankName;
        this.bankBranch = bankBranch;
        this.accountName = accountName;
        this.accountNumber = accountNumber;
        this.bankAddress = bankAddress;
        this.swiftCode = swiftCode;
        this.currency = currency;
        this.purpose = purpose;
        this.notes = notes;
    }

    public void markFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void deactivate() {
        this.status = SupplierStatus.INACTIVE;
    }

    private static void requireBankNameForBankAccount(BeneficiaryType type, String bankName) {
        if (type == BeneficiaryType.CHINESE_BANK_ACCOUNT && (bankName == null || bankName.isBlank())) {
            throw new IllegalArgumentException("bankName est obligatoire pour un CHINESE_BANK_ACCOUNT.");
        }
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public BeneficiaryType getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public String getProvince() {
        return province;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBankBranch() {
        return bankBranch;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getBankAddress() {
        return bankAddress;
    }

    public String getSwiftCode() {
        return swiftCode;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public SupplierStatus getStatus() {
        return status;
    }
}
