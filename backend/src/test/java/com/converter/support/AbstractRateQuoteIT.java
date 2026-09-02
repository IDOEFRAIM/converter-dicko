package com.converter.support;

import com.converter.rate.dto.PublishRateRequest;
import com.converter.rate.dto.RateSourceResponse;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.common.api.ApiResponse;
import com.converter.security.jwt.JwtService;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import com.converter.user.domain.Role;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import com.converter.user.repository.RoleRepository;
import com.converter.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

/**
 * Socle partage des tests d'integration Rate Engine + Quote : creation
 * de comptes, obtention de jetons, publication d'un taux et creation
 * d'un devis via HTTP — evite de dupliquer ce cablage dans chaque
 * classe de test de ce module.
 */
public abstract class AbstractRateQuoteIT extends AbstractIntegrationTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected SettingsService settingsService;

    /**
     * Remet la marge par defaut a zero, pour les tests qui verifient un
     * calcul "au taux de marche pur" (ex. l'exemple canonique de la
     * specification). La marge seedee par defaut (V8) est de 1,5 % ;
     * sans cet appel, un calcul suppose "taux pur" ne correspondrait
     * plus a la valeur attendue a la main.
     */
    protected void resetMarginToZero() {
        settingsService.update(SettingKey.DEFAULT_MARGIN_PERCENTAGE, "0",
                createUser(RoleCode.ADMIN).getId());
    }

    protected User createUser(RoleCode roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        User user = new User(uniquePhone(), passwordEncoder.encode("irrelevant-for-tests"), "Test", roleCode.name());
        user.addRole(role);
        return userRepository.saveAndFlush(user);
    }

    protected String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    protected String adminToken() {
        return tokenFor(createUser(RoleCode.ADMIN));
    }

    protected RateSourceResponse publishRate(String adminToken, String cfaPerCny) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<ApiResponse<RateSourceResponse>> response = restTemplate.exchange(
                "/api/admin/rates", HttpMethod.POST,
                new HttpEntity<>(new PublishRateRequest(new BigDecimal(cfaPerCny), "test"), headers),
                new ParameterizedTypeReference<ApiResponse<RateSourceResponse>>() {
                });
        return response.getBody().data();
    }

    protected ResponseEntity<ApiResponse<QuoteResponse>> createQuoteRaw(String userToken, CreateQuoteRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        return restTemplate.exchange(
                "/api/v1/quotes", HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<ApiResponse<QuoteResponse>>() {
                });
    }

    protected QuoteResponse createQuote(String userToken, CreateQuoteRequest request) {
        return createQuoteRaw(userToken, request).getBody().data();
    }
}
