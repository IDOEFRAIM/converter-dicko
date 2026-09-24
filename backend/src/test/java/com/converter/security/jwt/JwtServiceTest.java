package com.converter.security.jwt;

import com.converter.config.props.JwtProperties;
import com.converter.user.domain.Role;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires purs : aucun contexte Spring, {@link JwtService} est
 * instancie directement. Couvre la generation, la verification, le
 * rejet d'un jeton falsifie ou expire, et le refus d'un secret trop
 * court au demarrage.
 *
 * <p>Place volontairement dans le meme package que {@link JwtService} :
 * cela permet d'invoquer directement sa methode {@code @PostConstruct}
 * {@code init()}, package-privee, sans avoir a demarrer un contexte
 * Spring pour un test qui ne teste que de la logique pure.
 */
class JwtServiceTest {

    private static final String VALID_SECRET = "unit-test-signing-key-at-least-32-bytes-long!!";

    @Test
    void generateThenParse_roundTripsUserIdAndRoles() {
        JwtService jwtService = newService(VALID_SECRET, 120);
        User user = userWithRoles(RoleCode.USER, RoleCode.ADMIN);

        String token = jwtService.generateToken(user);
        JwtService.ParsedToken parsed = jwtService.parse(token);

        assertThat(parsed.userId()).isEqualTo(user.getId());
        assertThat(parsed.roles()).containsExactlyInAnyOrder(RoleCode.USER, RoleCode.ADMIN);
    }

    @Test
    void parse_withTamperedSignature_throws() {
        JwtService jwtService = newService(VALID_SECRET, 120);
        String token = jwtService.generateToken(userWithRoles(RoleCode.USER));
        // Modifie le PREMIER caractere de la signature (jamais le
        // dernier : en base64url sans remplissage, les derniers bits
        // d'une signature de 32 octets tombent parfois sur une position
        // de bourrage, et certaines paires de caracteres y decodent
        // alors vers les memes octets significatifs — un test qui
        // tamponnerait la fin serait alors silencieusement un no-op).
        // Le premier caractere du segment de signature, lui, code
        // toujours des bits pleinement significatifs.
        int lastDot = token.lastIndexOf('.');
        char firstSignatureChar = token.charAt(lastDot + 1);
        char replacement = firstSignatureChar == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, lastDot + 1) + replacement + token.substring(lastDot + 2);

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void parse_withTokenSignedByAnotherSecret_throws() {
        JwtService issuer = newService("a-completely-different-signing-key-32-bytes!!", 120);
        JwtService verifier = newService(VALID_SECRET, 120);
        String token = issuer.generateToken(userWithRoles(RoleCode.USER));

        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void parse_withExpiredToken_throws() {
        // Duree de vie negative : le jeton est deja expire des sa creation.
        JwtService expiringService = newService(VALID_SECRET, -1);
        String token = expiringService.generateToken(userWithRoles(RoleCode.USER));

        assertThatThrownBy(() -> expiringService.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void init_withSecretShorterThan32Bytes_throwsIllegalStateException() {
        JwtService shortSecretService = new JwtService(new JwtProperties("too-short", 120, "converter-api-test"));

        assertThatThrownBy(shortSecretService::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    // -----------------------------------------------------------------

    private static JwtService newService(String secret, long expirationMinutes) {
        JwtService service = new JwtService(new JwtProperties(secret, expirationMinutes, "converter-api-test"));
        service.init();
        return service;
    }

    /**
     * Construit un {@link User} pourvu d'un identifiant et de roles,
     * sans passer par la persistance.
     *
     * <p>{@code User.setId} est public (herite de {@code BaseEntity}) et
     * s'utilise directement. {@link Role}, en revanche, n'expose ni
     * constructeur public ni mutateur — c'est un referentiel ferme,
     * volontairement non constructible en dehors du chargement JPA — d'ou
     * le recours cible a la reflexion pour ce seul type, dans ce test.
     */
    private static User userWithRoles(RoleCode... codes) {
        User user = new User("+2250700000000", "hash", "Test", "User");
        user.setId(UUID.randomUUID());
        for (RoleCode code : codes) {
            user.addRole(roleOf(code));
        }
        return user;
    }

    private static Role roleOf(RoleCode code) {
        try {
            var constructor = Role.class.getDeclaredConstructor();
            // Le constructeur sans argument de Role est `protected` (reserve
            // a JPA) : setAccessible(true) est necessaire ici, en plus de
            // celui applique au champ, sinon la reflexion echoue avec un
            // IllegalAccessException des l'appel a newInstance().
            constructor.setAccessible(true);
            Role role = constructor.newInstance();

            // Role#equals/hashCode reposent uniquement sur `id`. Deux Role
            // de test tous deux construits avec un id nul seraient donc
            // consideres identiques par un HashSet<Role> (Objects.equals
            // (null, null) == true), quel que soit leur `code` — un
            // User.addRole(ADMIN) apres User.addRole(USER) remplacerait
            // silencieusement le premier au lieu de s'y ajouter. Les
            // identifiants reels amorces par V3__seed_roles.sql (USER=1,
            // ADMIN=2) sont reutilises ici pour rester realiste.
            Field idField = Role.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(role, (short) (code == RoleCode.ADMIN ? 2 : 1));

            Field codeField = Role.class.getDeclaredField("code");
            codeField.setAccessible(true);
            codeField.set(role, code);
            return role;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Impossible de construire un Role de test", ex);
        }
    }
}
