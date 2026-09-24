package com.converter.security;

import com.converter.common.api.ErrorResponse;
import com.converter.security.jwt.JwtService;
import com.converter.support.AbstractIntegrationTest;
import com.converter.user.domain.Role;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import com.converter.user.repository.RoleRepository;
import com.converter.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie la garantie centrale de la decision V5 : un blocage
 * administratif prend effet <b>immediatement</b>, sans attendre
 * l'expiration du jeton deja emis (2 heures par defaut).
 *
 * <p>Le scenario reproduit exactement la sequence critique :
 * un jeton valide est emis pendant que le compte est actif, puis le
 * compte est bloque "par un administrateur" (ici, directement en
 * base, pour isoler ce test de la disponibilite du module
 * {@code AdminUserController} teste par ailleurs), et le <b>meme
 * jeton, toujours non expire</b>, doit alors etre rejete.
 */
class BlockedUserIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void blockingAccount_invalidatesAlreadyIssuedToken_immediately() {
        Role userRole = roleRepository.findByCode(RoleCode.USER).orElseThrow();
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();

        User user = new User(uniquePhone(), passwordEncoder.encode("irrelevant"), "Test", "User");
        user.addRole(userRole);
        user = userRepository.saveAndFlush(user);

        // `blocked_by` porte une contrainte de cle etrangere vers `users` :
        // l'acteur doit exister reellement en base, pas seulement un UUID
        // genere a la volee.
        User admin = new User(uniquePhone(), passwordEncoder.encode("irrelevant"), "Admin", "Actor");
        admin.addRole(adminRole);
        admin = userRepository.saveAndFlush(admin);

        String token = jwtService.generateToken(user);

        // Le jeton fonctionne tant que le compte est actif.
        assertThat(callMe(token).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Un administrateur bloque le compte : simule directement en base,
        // exactement l'operation que UserService#block realiserait.
        User toBlock = userRepository.findById(user.getId()).orElseThrow();
        toBlock.block("Test de securite", admin.getId(), Instant.now());
        userRepository.saveAndFlush(toBlock);

        // Le MEME jeton, toujours dans sa fenetre de validite de 2 heures,
        // doit desormais etre rejete avec un code distinct (USER_BLOCKED),
        // et non l'erreur generique d'authentification.
        ResponseEntity<ErrorResponse> rejected = callMeForError(token);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejected.getBody().code()).isEqualTo("USER_BLOCKED");
    }

    private ResponseEntity<String> callMe(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<ErrorResponse> callMeForError(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);
    }
}
