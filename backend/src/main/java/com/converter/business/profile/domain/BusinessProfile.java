package com.converter.business.profile.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Preuve de l'existence d'une identite professionnelle pour un utilisateur — <b>pas</b> une
 * colonne {@code accountType} sur {@code User} (decision d'architecture validee, voir
 * {@code BusinessProfileService#isBusinessUser}) : un utilisateur est {@code BUSINESS} si et
 * seulement si ce profil existe pour lui, {@code PERSONAL} sinon. Un seul profil par utilisateur
 * ({@code UNIQUE(user_id)}, migration V26).
 *
 * <p>Donnee de profil pure : sa creation/modification ne cree, ne modifie et ne declenche jamais
 * de {@code Quote}/{@code Order}/{@code Payment}/{@code Settlement}/{@code Treasury}/
 * {@code Wallet} — voir {@code BusinessProfileService}, qui n'a aucune dependance vers ces
 * modules.
 *
 * <p><b>Hors perimetre, deliberement</b> (section 14/45 de la specification) : aucun statut
 * {@code PENDING_KYC}/{@code VERIFIED}/{@code REJECTED}/{@code SUSPENDED}, aucune verification
 * RCCM/fiscale, aucun document legal. {@code registrationNumber} reste optionnel — ce n'est pas
 * un moteur de conformite KYC entreprise.
 */
@Entity
@Table(name = "business_profiles")
public class BusinessProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "business_name", nullable = false, length = 160)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 24)
    private BusinessType businessType;

    /** Optionnel — jamais exige systematiquement (section 6 : pas de KYC reglementaire complet ici). */
    @Column(name = "registration_number", length = 60)
    private String registrationNumber;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BusinessProfile() {
        // Requis par JPA.
    }

    public BusinessProfile(UUID userId, String businessName, BusinessType businessType, String registrationNumber,
                           String country, String city, String address, Instant createdAt) {
        this.userId = userId;
        this.businessName = businessName;
        this.businessType = businessType;
        this.registrationNumber = registrationNumber;
        this.country = country;
        this.city = city;
        this.address = address;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(String businessName, BusinessType businessType, String registrationNumber, String country,
                       String city, String address, Instant now) {
        this.businessName = businessName;
        this.businessType = businessType;
        this.registrationNumber = registrationNumber;
        this.country = country;
        this.city = city;
        this.address = address;
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public BusinessType getBusinessType() {
        return businessType;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public String getAddress() {
        return address;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
