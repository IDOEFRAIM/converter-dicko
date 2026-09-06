package com.converter.support;

import com.converter.rate.cost.dto.CostRateConfigurationResponse;
import com.converter.rate.cost.dto.PublishCostRateConfigurationRequest;
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
import java.time.LocalDate;

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

    /**
     * KYC deja verifie par defaut : l'immense majorite des tests de cette suite ne portent pas
     * sur la verification d'identite elle-meme, seulement sur un flux (paiement, remboursement,
     * fournisseur, tracking...) qui cree incidemment un ordre — souvent au-dela du seuil KYC
     * (voir {@code SettingKey.KYC_REQUIRED_THRESHOLD_XOF}). Verifier par defaut evite de repeter
     * {@code verifyKyc(...)} dans des dizaines de fichiers sans rapport avec le KYC. Les tests qui
     * portent specifiquement sur la verification d'identite utilisent {@link #createUnverifiedUser}.
     */
    protected User createUser(RoleCode roleCode) {
        return verifyKyc(createUnverifiedUser(roleCode));
    }

    protected User createUnverifiedUser(RoleCode roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        User user = new User(uniquePhone(), passwordEncoder.encode("irrelevant-for-tests"), "Test", roleCode.name());
        user.addRole(role);
        return userRepository.saveAndFlush(user);
    }

    protected String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    /** Marque l'utilisateur comme verifie (KYC), necessaire pour un ordre au-dela du seuil configure. */
    protected User verifyKyc(User user) {
        user.verifyKyc(user.getId(), java.time.Instant.now());
        return userRepository.saveAndFlush(user);
    }

    protected String adminToken() {
        return tokenFor(createUser(RoleCode.ADMIN));
    }

    /**
     * Publie a la fois un taux manuel ({@code rate_sources}, toujours
     * utilise par {@code preferredrate}) et une configuration de cout
     * ({@code daily_cost_rate_configurations}, utilisee par la creation
     * de devis depuis la Phase 3.1) dont le {@code breakEvenRate}
     * calcule est <b>exactement</b> {@code cfaPerCny} : en choisissant
     * {@code rateUsdCny = 1} et des frais nuls, {@code breakEvenRate =
     * rateXofUsd / 1 = rateXofUsd}, ce qui permet a tous les tests
     * existants de continuer a raisonner sur "le taux publie" sans
     * changement, tout en exercant reellement le nouveau chemin de
     * cout.
     */
    protected RateSourceResponse publishRate(String adminToken, String cfaPerCny) {
        RateSourceResponse response = publishManualRateOnly(adminToken, cfaPerCny);
        publishCostRate(adminToken, cfaPerCny);
        return response;
    }

    /**
     * Publie UNIQUEMENT le taux manuel (`POST /api/admin/rates`), sans jamais toucher au chemin
     * de cout distinct (`CostRateAdminService`) — contrairement a {@link #publishRate}. Sert a
     * prouver que ce seul appel fait deja progresser {@code public_rate_snapshots} (voir le
     * correctif de {@code RateAdminService#publishManualRate}) : avant ce correctif, seule la
     * combinaison des deux appels de {@link #publishRate} le permettait.
     */
    protected RateSourceResponse publishManualRateOnly(String adminToken, String cfaPerCny) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<ApiResponse<RateSourceResponse>> response = restTemplate.exchange(
                "/api/admin/rates", HttpMethod.POST,
                new HttpEntity<>(new PublishRateRequest(new BigDecimal(cfaPerCny), "test"), headers),
                new ParameterizedTypeReference<ApiResponse<RateSourceResponse>>() {
                });
        return response.getBody().data();
    }

    protected CostRateConfigurationResponse publishCostRate(String adminToken, String breakEvenRate) {
        return publishCostRate(adminToken, breakEvenRate, "1", "0", "0", "1000000");
    }

    /** Variante avec les vrais parametres de la chaine XOF -&gt; USD -&gt; CNY, pour les tests qui veulent verifier le calcul du breakEvenRate lui-meme plutot que de le forcer a une valeur donnee. */
    protected CostRateConfigurationResponse publishCostRate(String adminToken, String rateXofUsd, String rateUsdCny,
                                                            String feeXofUsdPercent, String feeUsdCnyFixedUsd,
                                                            String referenceAmountXof) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<ApiResponse<CostRateConfigurationResponse>> response = restTemplate.exchange(
                "/api/admin/cost-rates", HttpMethod.POST,
                new HttpEntity<>(new PublishCostRateConfigurationRequest(
                        LocalDate.now(), new BigDecimal(rateXofUsd), new BigDecimal(rateUsdCny),
                        new BigDecimal(feeXofUsdPercent), new BigDecimal(feeUsdCnyFixedUsd),
                        new BigDecimal(referenceAmountXof), "test"), headers),
                new ParameterizedTypeReference<ApiResponse<CostRateConfigurationResponse>>() {
                });
        return response.getBody().data();
    }

    protected CostRateConfigurationResponse currentCostRateConfiguration(String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<ApiResponse<CostRateConfigurationResponse>> response = restTemplate.exchange(
                "/api/admin/cost-rates/current", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<CostRateConfigurationResponse>>() {
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
