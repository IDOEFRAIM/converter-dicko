package com.converter.order.receipt.web;

import com.converter.common.api.ErrorResponse;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/v1/orders/{id}/receipt} : authentification, ownership (404, jamais 403),
 * disponibilite conditionnee a {@code COMPLETED}, {@code Content-Type: application/pdf}.
 */
class OrderReceiptHttpIT extends AbstractOrderPipelineIT {

    private ResponseEntity<byte[]> receiptRaw(String token, UUID orderId) {
        HttpHeaders headers = auth(token);
        return restTemplate.exchange("/api/v1/orders/" + orderId + "/receipt", HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
    }

    private UUID completedOrderId(String admin, String user) {
        QuoteResponse quote = createAcceptedQuote(user, "1000000");
        OrderDetailResponse order = createOrder(user, quote.id(), bankBeneficiary());
        return completeOrder(admin, user, order.id(), order.amountXof().toPlainString(),
                "MM-HTTP-RECEIPT-" + UUID.randomUUID(), "CNY-HTTP-RECEIPT-" + UUID.randomUUID());
    }

    @Test
    void receipt_owner_returns200WithPdfContentType() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        UUID orderId = completedOrderId(admin, user);

        ResponseEntity<byte[]> response = receiptRaw(user, orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getBody()).isNotNull();
        assertThat(new String(response.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void receipt_owner_hasAttachmentContentDispositionWithFileName() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        UUID orderId = completedOrderId(admin, user);

        ResponseEntity<byte[]> response = receiptRaw(user, orderId);

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).contains("attachment");
        assertThat(disposition).contains("transaction-");
        assertThat(disposition).contains(".pdf");
    }

    @Test
    void receipt_anonymous_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/orders/" + UUID.randomUUID() + "/receipt", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void receipt_unknownOrder_returns404() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/orders/" + UUID.randomUUID() + "/receipt", HttpMethod.GET,
                new HttpEntity<>(auth(user)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void receipt_anotherUsersOrder_returns404_neverRevealsExistence() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String owner = tokenFor(createUser(RoleCode.USER));
        String stranger = tokenFor(createUser(RoleCode.USER));
        UUID orderId = completedOrderId(admin, owner);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/orders/" + orderId + "/receipt", HttpMethod.GET,
                new HttpEntity<>(auth(stranger)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void receipt_orderNotCompleted_returns409() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "1000000");
        OrderDetailResponse order = createOrder(user, quote.id(), bankBeneficiary());
        // Encore AWAITING_PAYMENT -- aucun paiement soumis.

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/orders/" + order.id() + "/receipt", HttpMethod.GET,
                new HttpEntity<>(auth(user)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INVALID_ORDER_STATE");
    }
}
