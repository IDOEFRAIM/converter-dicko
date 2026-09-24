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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie les deux barrieres qui protegent {@code /api/admin/**} :
 * l'authentification (401 sans jeton) et le controle de role
 * (403 pour un compte {@code USER}), conformement aux contraintes de
 * la Phase 2 ("les endpoints /api/admin/** doivent etre reserves au
 * role ADMIN").
 *
 * <p>Les comptes de test sont crees directement via les repositories
 * plutot que via {@code /api/auth/register}, qui n'attribue jamais le
 * role ADMIN : c'est precisement la garantie que ce test verifie a un
 * autre niveau (aucune API n'expose de moyen de s'auto-promouvoir).
 */
class AdminEndpointSecurityIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void adminEndpoint_withoutToken_returns401() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity(
                "/api/admin/users", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void adminEndpoint_withUserRole_returns403() {
        User user = createUser(RoleCode.USER);
        String token = jwtService.generateToken(user);

        ResponseEntity<ErrorResponse> response = get("/api/admin/users", token, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void adminEndpoint_withAdminRole_returns200() {
        User admin = createUser(RoleCode.ADMIN);
        String token = jwtService.generateToken(admin);

        ResponseEntity<String> response = get("/api/admin/users", token, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void ownOrdersEndpointStyleResource_withUserRole_isNotBlockedByAdminRule() {
        // Garde-fou inverse : /api/auth/me, hors de /api/admin/**, doit
        // rester accessible a un simple USER. Une regle CORS/securite trop
        // large sur /api/admin/** ne doit jamais deborder sur le reste de
        // l'API.
        User user = createUser(RoleCode.USER);
        String token = jwtService.generateToken(user);

        ResponseEntity<String> response = get("/api/auth/me", token, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private <T> ResponseEntity<T> get(String path, String token, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), type);
    }

    @Transactional
    protected User createUser(RoleCode roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        User user = new User(uniquePhone(), passwordEncoder.encode("irrelevant-for-this-test"),
                "Test", roleCode.name());
        user.addRole(role);
        return userRepository.saveAndFlush(user);
    }
}
