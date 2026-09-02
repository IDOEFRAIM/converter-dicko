package com.converter.common.idempotency;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.dto.BeneficiaryRequest;
import com.converter.order.dto.CreateOrderRequest;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.dto.TreasuryAccountResponse;
import com.converter.treasury.dto.TreasuryAdjustmentRequest;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie l'idempotence HTTP reelle (voir {@code docs/AUDIT_BUSINESS_LOGIC.md} §17) sur les
 * endpoints critiques : un rejeu avec la meme cle {@code Idempotency-Key} et le meme corps ne
 * doit jamais re-executer l'operation financiere sous-jacente, et doit rejouer la reponse
 * d'origine telle quelle. L'en-tete restant optionnel, les autres tests d'integration (qui ne le
 * fournissent jamais) continuent de verifier que le comportement anterieur est inchange.
 */
class IdempotencyGuardIT extends AbstractOrderPipelineIT {

    @Test
    void createOrder_replayedWithSameIdempotencyKey_neverCreatesASecondOrder() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        CreateOrderRequest request = new CreateOrderRequest(quote.id(), alipayBeneficiary(), "idem-test");
        String idemKey = "order-create-" + UUID.randomUUID();

        ResponseEntity<ApiResponse<OrderDetailResponse>> first = postOrder(user, request, idemKey);
        ResponseEntity<ApiResponse<OrderDetailResponse>> second = postOrder(user, request, idemKey);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Le rejeu conserve le meme code HTTP 201 d'origine (ce n'est pas une lecture, mais la
        // memoire exacte du resultat de la premiere execution) et le MEME identifiant d'ordre.
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().data().id()).isEqualTo(first.getBody().data().id());
        assertThat(second.getBody().data().reference()).isEqualTo(first.getBody().data().reference());

        // Un seul ordre existe reellement pour ce devis : le second appel n'a pas ré-execute
        // OrderService.create (qui aurait echoue en QUOTE_ALREADY_USED, pas rejoue silencieusement).
        ResponseEntity<ApiResponse<com.converter.common.api.PageResponse<com.converter.order.dto.OrderSummaryResponse>>> mine =
                restTemplate.exchange("/api/v1/orders", HttpMethod.GET, new HttpEntity<>(auth(user)),
                        new ParameterizedTypeReference<>() {
                        });
        long matching = mine.getBody().data().content().stream()
                .filter(o -> o.id().equals(first.getBody().data().id()))
                .count();
        assertThat(matching).isEqualTo(1);
    }

    @Test
    void createOrder_sameKeyWithDifferentBody_isRejectedWithoutExecutingTwice() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quoteA = createAcceptedQuote(user, "100000");
        QuoteResponse quoteB = createAcceptedQuote(user, "50000");
        String idemKey = "order-create-conflict-" + UUID.randomUUID();

        ResponseEntity<ApiResponse<OrderDetailResponse>> first =
                postOrder(user, new CreateOrderRequest(quoteA.id(), alipayBeneficiary(), "first"), idemKey);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        HttpHeaders headers = auth(user);
        headers.set("Idempotency-Key", idemKey);
        ResponseEntity<ErrorResponse> conflict = restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest(quoteB.id(), alipayBeneficiary(), "second-different-body"), headers),
                ErrorResponse.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        // Le second devis (quoteB), lui, n'a jamais ete consomme : la garde a rejete la requete
        // avant meme d'appeler OrderService.create.
        ResponseEntity<ApiResponse<QuoteResponse>> reloadedQuoteB = restTemplate.exchange(
                "/api/v1/quotes/" + quoteB.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(reloadedQuoteB.getBody().data().status().name()).isEqualTo("ACCEPTED");
    }

    @Test
    void createOrder_withoutIdempotencyKey_behavesExactlyAsBefore() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");

        ResponseEntity<ApiResponse<OrderDetailResponse>> response = postOrder(user,
                new CreateOrderRequest(quote.id(), alipayBeneficiary(), "no-key"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void treasuryDeposit_replayedWithSameIdempotencyKey_creditsExactlyOnce() {
        String admin = adminToken();
        String idemKey = "treasury-deposit-" + UUID.randomUUID();
        TreasuryAdjustmentRequest request = new TreasuryAdjustmentRequest(Currency.CNY, new BigDecimal("777"), "test idempotence");
        BigDecimal before = treasurySnapshot(admin, Currency.CNY).balance();

        postDeposit(admin, request, idemKey);
        postDeposit(admin, request, idemKey);
        postDeposit(admin, request, idemKey);

        // Sans filet SQL sur un mouvement de tresorerie manuel (a la difference de la reservation
        // liee a un ordre), c'est UNIQUEMENT l'idempotence applicative qui empeche un triple credit.
        BigDecimal after = treasurySnapshot(admin, Currency.CNY).balance();
        assertThat(after.subtract(before)).isEqualByComparingTo("777.00");
    }

    @Test
    void treasuryDeposit_sameKeyAcrossDifferentAdmins_areIndependent() {
        String adminA = tokenFor(createUser(RoleCode.ADMIN));
        String adminB = tokenFor(createUser(RoleCode.ADMIN));
        String sharedLiteralKey = "shared-literal-key";
        TreasuryAdjustmentRequest request = new TreasuryAdjustmentRequest(Currency.CNY, new BigDecimal("10"), "cross-user");
        BigDecimal before = treasurySnapshot(adminA, Currency.CNY).balance();

        // La cle d'idempotence est toujours partitionnee par (userId, endpoint, cle) — jamais par
        // la seule cle fournie par le client : deux administrateurs distincts utilisant la meme
        // valeur litterale ne doivent jamais se bloquer ni partager une reponse en cache.
        ResponseEntity<ApiResponse<TreasuryAccountResponse>> fromA = postDeposit(adminA, request, sharedLiteralKey);
        ResponseEntity<ApiResponse<TreasuryAccountResponse>> fromB = postDeposit(adminB, request, sharedLiteralKey);

        assertThat(fromA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fromB.getStatusCode()).isEqualTo(HttpStatus.OK);
        BigDecimal after = treasurySnapshot(adminA, Currency.CNY).balance();
        assertThat(after.subtract(before)).isEqualByComparingTo("20.00");
    }

    /**
     * Passe 2, P2-3 : un echec metier ne doit pas mettre la cle en cache ni la bloquer. Premier
     * appel : devis pas encore accepte -> 409, la capture est LIBEREE. Deuxieme appel, meme cle,
     * apres correction (devis accepte) -> 201. « meme cle, meme requete » redonne bien un
     * resultat coherent, et un echec transitoire n'est jamais fige.
     */
    @Test
    void createOrder_failedThenRetriedWithSameKeyAfterFixingConditions_succeeds() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        // Devis cree mais NON accepte -> createOrder echoue (QUOTE_NOT_ACCEPTED).
        QuoteResponse quote = createQuote(user,
                new com.converter.quote.dto.CreateQuoteRequest(
                        com.converter.quote.domain.QuoteDirection.SEND_XOF, new BigDecimal("100000"), null));
        CreateOrderRequest request = new CreateOrderRequest(quote.id(), alipayBeneficiary(), "retry-test");
        String idemKey = "order-fail-retry-" + UUID.randomUUID();

        HttpHeaders headers = auth(user);
        headers.set("Idempotency-Key", idemKey);
        ResponseEntity<ErrorResponse> firstAttempt = restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, headers), ErrorResponse.class);
        assertThat(firstAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(firstAttempt.getBody().code()).isEqualTo("QUOTE_NOT_ACCEPTED");

        // Correction : on accepte le devis, puis on rejoue avec LA MEME cle.
        restTemplate.exchange("/api/v1/quotes/" + quote.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(auth(user)), new ParameterizedTypeReference<ApiResponse<QuoteResponse>>() {
                });

        ResponseEntity<ApiResponse<OrderDetailResponse>> retry = postOrder(user, request, idemKey);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getBody().data().status().name()).isEqualTo("AWAITING_PAYMENT");
    }

    // -----------------------------------------------------------------

    private ResponseEntity<ApiResponse<OrderDetailResponse>> postOrder(String userToken, CreateOrderRequest request, String idemKey) {
        HttpHeaders headers = auth(userToken);
        if (idemKey != null) {
            headers.set("Idempotency-Key", idemKey);
        }
        return restTemplate.exchange("/api/v1/orders", HttpMethod.POST, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<ApiResponse<TreasuryAccountResponse>> postDeposit(String adminToken, TreasuryAdjustmentRequest request, String idemKey) {
        HttpHeaders headers = auth(adminToken);
        headers.set("Idempotency-Key", idemKey);
        return restTemplate.exchange("/api/admin/treasury/deposit", HttpMethod.POST, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {
                });
    }
}
