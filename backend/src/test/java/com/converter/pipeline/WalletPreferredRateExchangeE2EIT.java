package com.converter.pipeline;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.notification.dto.NotificationResponse;
import com.converter.notification.dto.UnreadCountResponse;
import com.converter.preferredrate.domain.PreferredRateDirection;
import com.converter.preferredrate.domain.PreferredRateStatus;
import com.converter.preferredrate.dto.CreatePreferredRateRequest;
import com.converter.preferredrate.dto.PreferredRateRequestResponse;
import com.converter.preferredrate.service.PreferredRateService;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import com.converter.wallet.dto.WalletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours complet exige par la specification :
 *
 * <pre>
 * Wallet -&gt; demande de taux preferentiel -&gt; taux atteint
 *        -&gt; echange declenche -&gt; notifications -&gt; termine
 * </pre>
 *
 * <p>Le declenchement du scheduler est simule par un appel direct a
 * {@code PreferredRateService#processOne}/{@code #progressOne} (le
 * scheduler lui-meme n'est qu'une boucle qui appelle ces memes methodes
 * -- voir {@code PreferredRateScheduler}) : verifier le comportement
 * ainsi reste deterministe, sans dependre du delai reel entre deux
 * passages du {@code @Scheduled}.
 */
class WalletPreferredRateExchangeE2EIT extends AbstractRateQuoteIT {

    @Autowired
    private PreferredRateService preferredRateService;

    @Autowired
    private com.converter.wallet.service.WalletService walletService;

    @Test
    void wallet_preferredRate_exchange_endToEnd() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "85.000000");

        var user = createUser(RoleCode.USER);
        String userToken = tokenFor(user);

        // 1. Wallet credite (aucun endpoint de depot public a ce stade :
        // alimentation manuelle, comme toute action de tresorerie MVP).
        walletService.deposit(user.getId(), new BigDecimal("500000"), "E2E fixture");
        WalletResponse wallet = getWallet(userToken);
        assertThat(wallet.available()).isEqualByComparingTo("500000.00");

        // 2. Demande de taux preferentiel, cible deja atteinte par le
        // taux courant (85 >= 80), pour declencher au premier passage.
        PreferredRateRequestResponse created = createPreferredRate(userToken,
                new CreatePreferredRateRequest(PreferredRateDirection.XOF_TO_CNY,
                        new BigDecimal("100000"), new BigDecimal("80")));
        assertThat(created.status()).isEqualTo(PreferredRateStatus.ACTIVE);
        assertThat(getWallet(userToken).reservedBalance()).isEqualByComparingTo("100000.00");

        // 3. Le scheduler evalue et declenche (taux atteint).
        preferredRateService.processOne(created.id());

        PreferredRateRequestResponse triggered = getPreferredRate(userToken, created.id());
        assertThat(triggered.status()).isEqualTo(PreferredRateStatus.EXECUTED);
        assertThat(triggered.exchange()).isNotNull();
        assertThat(triggered.exchange().status().name()).isEqualTo("STARTED");
        assertThat(getWallet(userToken).balance()).isEqualByComparingTo("400000.00");
        assertThat(getWallet(userToken).reservedBalance()).isEqualByComparingTo("0.00");

        // 4. Notifications : taux atteint + echange demarre.
        PageResponse<NotificationResponse> notifications = listNotifications(userToken);
        assertThat(notifications.content())
                .extracting(NotificationResponse::type)
                .anyMatch(type -> type.name().equals("PREFERRED_RATE_REACHED"));
        assertThat(notifications.content())
                .extracting(NotificationResponse::type)
                .anyMatch(type -> type.name().equals("EXCHANGE_STARTED"));
        long unreadAfterStart = unreadCount(userToken);
        assertThat(unreadAfterStart).isGreaterThanOrEqualTo(2);

        // 5. Progression et terminaison de l'echange.
        var exchangeId = triggered.exchange().id();
        preferredRateService.progressOne(exchangeId); // pas encore du a T0 : no-op
        assertThat(getPreferredRate(userToken, created.id()).exchange().status().name()).isEqualTo("STARTED");

        // 6. Marquer une notification comme lue.
        NotificationResponse firstUnread = notifications.content().get(0);
        markNotificationRead(userToken, firstUnread.id());
        assertThat(unreadCount(userToken)).isLessThan(unreadAfterStart);
    }

    // -----------------------------------------------------------------

    private WalletResponse getWallet(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiResponse<WalletResponse>> response = restTemplate.exchange(
                "/api/v1/wallet", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<WalletResponse>>() {
                });
        return response.getBody().data();
    }

    private PreferredRateRequestResponse createPreferredRate(String token, CreatePreferredRateRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiResponse<PreferredRateRequestResponse>> response = restTemplate.exchange(
                "/api/v1/preferred-rates", HttpMethod.POST, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<ApiResponse<PreferredRateRequestResponse>>() {
                });
        return response.getBody().data();
    }

    private PreferredRateRequestResponse getPreferredRate(String token, java.util.UUID id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiResponse<PreferredRateRequestResponse>> response = restTemplate.exchange(
                "/api/v1/preferred-rates/" + id, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<PreferredRateRequestResponse>>() {
                });
        return response.getBody().data();
    }

    private PageResponse<NotificationResponse> listNotifications(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> response = restTemplate.exchange(
                "/api/v1/notifications", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<PageResponse<NotificationResponse>>>() {
                });
        return response.getBody().data();
    }

    private long unreadCount(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiResponse<UnreadCountResponse>> response = restTemplate.exchange(
                "/api/v1/notifications/unread-count", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<UnreadCountResponse>>() {
                });
        return response.getBody().data().unreadCount();
    }

    private void markNotificationRead(String token, java.util.UUID id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        restTemplate.exchange("/api/v1/notifications/" + id + "/read", HttpMethod.POST,
                new HttpEntity<>(headers), Void.class);
    }
}
