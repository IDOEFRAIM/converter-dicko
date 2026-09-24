package com.converter.security.jwt;

import com.converter.config.props.JwtProperties;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Emission et verification des jetons d'acces.
 *
 * <p>Le MVP ne porte pas de refresh token (decision V5) : un jeton dure
 * 2 heures et le seul mecanisme de revocation immediate est la
 * revalidation du statut du compte en base a chaque requete, realisee
 * par {@link JwtAuthenticationFilter}. Le sujet du jeton (claim
 * {@code sub}) est l'UUID du compte, jamais son numero de telephone,
 * afin qu'un changement de numero n'invalide pas silencieusement des
 * jetons emis.
 *
 * <p>Le format HS256 suffit pour un service monolithique unique
 * verifiant ses propres jetons. Un passage a un couple de cles
 * asymetriques (RS256) ne serait justifie que si un autre service
 * devait un jour verifier les jetons sans pouvoir les emettre.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLES = "roles";

    private final JwtProperties properties;
    private SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < JwtProperties.MIN_SECRET_LENGTH) {
            // Echec net au demarrage plutot qu'une cle faible en production :
            // HMAC-SHA256 avec un secret court est trivialement cassable.
            throw new IllegalStateException(
                    "app.jwt.secret doit contenir au moins " + JwtProperties.MIN_SECRET_LENGTH
                            + " octets (JWT_SECRET actuel : " + secretBytes.length + " octets).");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(properties.expirationMinutes()));

        List<String> roles = user.getRoles().stream()
                .map(role -> role.getCode().name())
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_ROLES, roles)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Decode et verifie le jeton (signature, expiration, emetteur).
     *
     * @throws JwtException si le jeton est invalide, expire ou mal signe
     */
    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        @SuppressWarnings("unchecked")
        List<String> rawRoles = claims.get(CLAIM_ROLES, List.class);
        Set<RoleCode> roles = rawRoles == null
                ? Set.of()
                : rawRoles.stream().map(RoleCode::valueOf).collect(Collectors.toSet());

        return new ParsedToken(userId, roles);
    }

    public Duration expirationDuration() {
        return Duration.ofMinutes(properties.expirationMinutes());
    }

    /** Contenu utile extrait d'un jeton valide. */
    public record ParsedToken(UUID userId, Set<RoleCode> roles) {
    }
}
