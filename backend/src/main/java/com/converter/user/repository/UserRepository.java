package com.converter.user.repository;

import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import com.converter.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    /**
     * Recherche paginee de l'espace d'administration.
     *
     * <p>Le filtrage est realise en SQL et non en memoire : charger
     * l'ensemble des comptes pour filtrer cote Java ne tiendrait pas la
     * charge et exposerait des donnees au-dela du besoin.
     *
     * <p>{@code pattern} est un motif LIKE deja entoure de {@code %},
     * jamais {@code null} — construit par {@code UserService} avant
     * l'appel. Un {@code CONCAT('%', :search, '%')} avec un parametre
     * {@code :search} nul fait echouer PostgreSQL a la preparation de
     * la requete ({@code function lower(bytea) does not exist}) : le
     * pilote JDBC ne peut pas deduire le type d'un parametre nul
     * combine par concatenation, meme dans une branche jamais executee
     * au runtime — la requete est preparee une seule fois, avant que
     * la valeur ne soit connue. Passer un motif "%" deja construit
     * (qui filtre alors sur "tout") evite le probleme a la racine.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:status IS NULL OR u.status = :status)
              AND (LOWER(u.firstName) LIKE LOWER(:pattern)
                   OR LOWER(u.lastName) LIKE LOWER(:pattern)
                   OR u.phone LIKE :pattern)
            """)
    Page<User> search(@Param("status") UserStatus status,
                      @Param("pattern") String pattern,
                      Pageable pageable);

    /** Sert a determiner si l'amorcage d'un administrateur est necessaire. */
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.code = :code")
    long countByRole(@Param("code") RoleCode code);

    /**
     * Numero synthetique unique (format "+9" + 14 chiffres, jamais un
     * indicatif pays reel) pour {@code User#anonymizeForDeletion} — voir
     * {@code deleted_account_phone_seq}, V40. Respecte {@code
     * ck_users_phone_format} : impossible d'y ecrire un texte libre.
     */
    @Query(value = "SELECT '+9' || lpad(nextval('deleted_account_phone_seq')::text, 14, '0')",
            nativeQuery = true)
    String nextDeletedAccountPhone();
}
