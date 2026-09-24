package com.converter.security;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exposition Actuator — audit de fermeture, Phase 10.
 *
 * <p>{@code /actuator/health/**} reste public (verifie par ailleurs par le
 * healthcheck Docker et {@code SecurityConfig}). Les autres endpoints
 * exposes ({@code info}, {@code metrics}) ne doivent jamais etre
 * accessibles anonymement — seule la liste explicite {@code health,info,
 * metrics} est exposee (voir {@code application.yml}) : aucun endpoint
 * sensible ({@code env}, {@code beans}, {@code configprops},
 * {@code mappings}, {@code heapdump}) n'est meme enregistre.
 */
class ActuatorSecurityIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void health_isPubliclyAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void metrics_anonymousAccess_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/metrics", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void metrics_authenticatedAccess_isAllowed() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenForNewUser());

        ResponseEntity<String> response = restTemplate.exchange(
                "/actuator/metrics", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void sensitiveEndpoints_areNotExposedAtAll() {
        // Meme authentifie (donc a fortiori anonyme) : ces endpoints ne sont
        // pas dans management.endpoints.web.exposure.include, Spring Boot
        // renvoie 404 (route jamais enregistree), pas 401/403.
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenForNewUser());

        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/mappings", "/actuator/heapdump"}) {
            ResponseEntity<String> response = restTemplate.exchange(
                    path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            assertThat(response.getStatusCode()).as("path %s", path).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private String tokenForNewUser() {
        Role role = roleRepository.findByCode(RoleCode.USER).orElseThrow();
        User user = new User(uniquePhone(), passwordEncoder.encode("irrelevant-for-tests"), "Test", "User");
        user.addRole(role);
        return jwtService.generateToken(userRepository.saveAndFlush(user));
    }
}
