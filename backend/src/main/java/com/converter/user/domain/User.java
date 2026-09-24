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

    /**
     * Nullable depuis {@code V34} : un compte cree via Google Sign-In n'en a
     * jamais (voir {@link #googleSubject}). {@code ck_users_has_auth_method}
     * garantit en base qu'au moins l'un des deux est toujours present.
     */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /**
     * Identifiant stable ("sub") du compte Google associe, deja verifie par
     * {@code GoogleTokenVerifierService} avant toute ecriture ici — jamais
     * pose a partir d'une valeur non authentifiee. {@code null} pour un
     * compte phone+mot de passe classique.
     */
    @Column(name = "google_subject", length = 255, unique = true)
    private String googleSubject;

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

    @Column(name = "deleted_at")
    private Instant deletedAt;

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

    /**
     * Profil d'experience marketing (habillage mobile uniquement) — voir
     * {@link ExperienceProfile}. Jamais controle par un administrateur,
     * contrairement a {@link #kycVerified}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "experience_profile", nullable = false, length = 20)
    private ExperienceProfile experienceProfile = ExperienceProfile.PRO;

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

    /**
     * Compte cree via Google Sign-In : jamais de mot de passe, le numero de
     * telephone est fourni separement par l'utilisateur (voir
     * {@code AuthService#completeGoogleSignUp}) puisque Google ne le
     * transmet pas.
     */
    public static User googleSignUp(String phone, String googleSubject, String email,
                                    String firstName, String lastName) {
        User user = new User(phone, null, firstName, lastName);
        user.googleSubject = googleSubject;
        user.email = email;
        return user;
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

    /**
     * Anonymise ce compte a la demande de son titulaire (voir
     * {@code AccountDeletionService}).
     *
     * <p>{@code syntheticPhone} DOIT respecter {@code ck_users_phone_format}
     * (le vrai numero est ainsi libere pour une future inscription — voir
     * {@code deleted_account_phone_seq}, V40) : cette entite ne genere
     * jamais elle-meme de valeur, seul l'appelant (avec acces au
     * repository) le peut. {@code passwordAnonymizationHash} DOIT etre un
     * condensat d'une valeur aleatoire jamais transmise a l'utilisateur
     * (jamais {@code null} : {@code ck_users_has_auth_method} exige au
     * moins un moyen d'authentification present, et {@code googleSubject}
     * est justement retire ici). Ne touche jamais aux tables financieres
     * qui referencent {@link #getId()} — elles restent intactes (voir la
     * Javadoc de {@code V40__account_deletion.sql}).
     */
    public void anonymizeForDeletion(String syntheticPhone, String passwordAnonymizationHash, Instant at) {
        this.status = UserStatus.DELETED;
        this.deletedAt = at;
        this.phone = syntheticPhone;
        this.passwordHash = passwordAnonymizationHash;
        this.googleSubject = null;
        this.firstName = "Compte";
        this.lastName = "supprime";
        this.email = null;
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

    public void changeExperienceProfile(ExperienceProfile profile) {
        this.experienceProfile = profile;
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

    public String getGoogleSubject() {
        return googleSubject;
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

    public Instant getDeletedAt() {
        return deletedAt;
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

    public ExperienceProfile getExperienceProfile() {
        return experienceProfile;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
