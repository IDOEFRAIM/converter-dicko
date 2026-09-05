package com.converter.user.domain;

import com.converter.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Compte utilisateur, client comme administrateur.
 *
 * <p>Le mot de passe n'existe ici que sous forme de condensat BCrypt.
 * Aucun accesseur ne l'expose vers la couche web : les DTO de sortie ne
 * comportent tout simplement pas le champ, et un test verifie qu'aucune
 * reponse JSON ne contient {@code passwordHash}.
 *
 * <p>Les roles sont charges en {@code EAGER} : ils sont necessaires a
 * chaque requete authentifiee pour construire les autorites, et leur
 * volume est celui d'un referentiel ferme (deux lignes).
 */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(name = "phone", nullable = false, length = 20, unique = true)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "email", length = 160)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    @Column(name = "blocked_by")
    private UUID blockedBy;

    /**
     * Verification d'identite (KYC). Volontairement minimal — un simple drapeau pose par un
     * administrateur, pas un moteur de conformite complet (aucun document/upload/OCR). Devient
     * obligatoire pour creer un ordre au-dela de {@code SettingKey.KYC_REQUIRED_THRESHOLD_XOF}
     * (voir {@code OrderService}).
     */
    @Column(name = "kyc_verified", nullable = false)
    private boolean kycVerified = false;

    @Column(name = "kyc_verified_at")
    private Instant kycVerifiedAt;

    @Column(name = "kyc_verified_by")
    private UUID kycVerifiedBy;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
        // Requis par JPA.
    }

    public User(String phone, String passwordHash, String firstName, String lastName) {
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = UserStatus.ACTIVE;
    }

    // -----------------------------------------------------------------
    // Comportement metier
    // -----------------------------------------------------------------

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }

    public boolean hasRole(RoleCode code) {
        return roles.stream().anyMatch(role -> role.getCode() == code);
    }

    public void block(String reason, UUID actorId, Instant at) {
        this.status = UserStatus.BLOCKED;
        this.blockedReason = reason;
        this.blockedBy = actorId;
        this.blockedAt = at;
    }

    public void unblock() {
        this.status = UserStatus.ACTIVE;
        this.blockedReason = null;
        this.blockedBy = null;
        this.blockedAt = null;
    }

    public void verifyKyc(UUID actorId, Instant at) {
        this.kycVerified = true;
        this.kycVerifiedAt = at;
        this.kycVerifiedBy = actorId;
    }

    public void revokeKyc() {
        this.kycVerified = false;
        this.kycVerifiedAt = null;
        this.kycVerifiedBy = null;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    // -----------------------------------------------------------------
    // Accesseurs
    // -----------------------------------------------------------------

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public UUID getBlockedBy() {
        return blockedBy;
    }

    public boolean isKycVerified() {
        return kycVerified;
    }

    public Instant getKycVerifiedAt() {
        return kycVerifiedAt;
    }

    public UUID getKycVerifiedBy() {
        return kycVerifiedBy;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
