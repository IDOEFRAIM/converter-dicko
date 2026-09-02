package com.converter.pipeline;

import com.converter.order.domain.OrderStatus;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.payment.domain.PaymentStatus;
import com.converter.payment.dto.PaymentResponse;
import com.converter.quote.domain.QuoteStatus;
import com.converter.quote.dto.QuoteResponse;
import com.converter.settlement.domain.SettlementStatus;
import com.converter.settlement.dto.SettlementResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.dto.TreasuryAccountResponse;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flux complet exige par le cahier des charges de finalisation du MVP :
 *
 * <pre>
 * Quote -&gt; Accept -&gt; Order -&gt; Payment -&gt; Payment CONFIRMED
 *       -&gt; Settlement -&gt; Settlement EXECUTED
 * </pre>
 *
 * Verifie a chaque etape l'etat de l'ordre ET l'effet correspondant sur
 * la tresorerie, de la reservation initiale jusqu'a la consommation
 * finale.
 */
class FullPipelineIT extends AbstractOrderPipelineIT {

    @Test
    void quoteToSettlementExecuted_endToEnd() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        TreasuryAccountResponse cnyInitial = treasurySnapshot(admin, Currency.CNY);
        TreasuryAccountResponse xofInitial = treasurySnapshot(admin, Currency.XOF);

        // 1. Quote
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        assertThat(quote.status()).isEqualTo(QuoteStatus.ACCEPTED);

        // 2. Order (reserve la liquidite CNY)
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        assertThat(order.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        TreasuryAccountResponse cnyAfterOrder = treasurySnapshot(admin, Currency.CNY);
        assertThat(cnyInitial.available().subtract(cnyAfterOrder.available()))
                .isEqualByComparingTo(order.amountCny());

        // 3. Payment soumis
        PaymentResponse payment = submitPayment(user, order.id(), order.amountXof().toPlainString(), "MM-E2E-001");
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUBMITTED);
        assertThat(orderStatus(user, order.id())).isEqualTo(OrderStatus.PAYMENT_SUBMITTED);

        // 4. Preuve + confirmation -> Payment CONFIRMED
        uploadProof(user, payment.id());
        PaymentResponse confirmed = confirmPayment(admin, payment.id());
        assertThat(confirmed.status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(orderStatus(user, order.id())).isEqualTo(OrderStatus.PAYMENT_VERIFIED);

        TreasuryAccountResponse xofAfterConfirm = treasurySnapshot(admin, Currency.XOF);
        assertThat(xofAfterConfirm.balance().subtract(xofInitial.balance()))
                .isEqualByComparingTo(order.amountXof());

        // 5. Settlement cree (Order -> PROCESSING)
        SettlementResponse settlement = createSettlement(admin, order.id());
        assertThat(settlement.status()).isEqualTo(SettlementStatus.PENDING);
        assertThat(orderStatus(user, order.id())).isEqualTo(OrderStatus.PROCESSING);
        assertThat(settlement.beneficiaryFullName()).isEqualTo("Zhang San");
        assertThat(settlement.amountCny()).isEqualByComparingTo(order.amountCny());

        // 6. Settlement execute (Order -> COMPLETED, CNY consommee)
        uploadSettlementProofRaw(admin, settlement.id());
        SettlementResponse executed = executeSettlement(admin, settlement.id(), "CNY-PAYOUT-E2E-001");
        assertThat(executed.status()).isEqualTo(SettlementStatus.EXECUTED);
        assertThat(orderStatus(user, order.id())).isEqualTo(OrderStatus.COMPLETED);

        TreasuryAccountResponse cnyFinal = treasurySnapshot(admin, Currency.CNY);
        // La reservation initiale a ete integralement consommee : le
        // solde disponible final egale le solde disponible initial
        // moins le montant reellement decaisse (aucune fuite, aucun
        // double decaissement).
        assertThat(cnyInitial.balance().subtract(cnyFinal.balance())).isEqualByComparingTo(order.amountCny());
        assertThat(cnyFinal.reservedBalance()).isEqualByComparingTo(cnyInitial.reservedBalance());
        assertThat(cnyFinal.available()).isEqualByComparingTo(cnyAfterOrder.available());
    }

    private OrderStatus orderStatus(String userToken, java.util.UUID orderId) {
        var response = restTemplate.exchange("/api/v1/orders/" + orderId, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(auth(userToken)),
                new org.springframework.core.ParameterizedTypeReference<com.converter.common.api.ApiResponse<OrderDetailResponse>>() {
                });
        return response.getBody().data().status();
    }

    private void uploadSettlementProofRaw(String adminToken, java.util.UUID settlementId) {
        org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        body.add("file", new org.springframework.core.io.ByteArrayResource(jpeg) {
            @Override
            public String getFilename() {
                return "e2e-settlement-proof.jpg";
            }
        });
        org.springframework.http.HttpHeaders headers = auth(adminToken);
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        restTemplate.exchange("/api/admin/settlements/" + settlementId + "/proofs",
                org.springframework.http.HttpMethod.POST, new org.springframework.http.HttpEntity<>(body, headers),
                String.class);
    }
}
