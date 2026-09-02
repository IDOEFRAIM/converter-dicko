package com.converter.payment.dto;

import com.converter.payment.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Declaration du paiement XOF effectue hors plateforme")
public record SubmitPaymentRequest(

        @NotNull(message = "La methode de paiement est obligatoire")
        PaymentMethod method,

        @NotNull(message = "Le montant recu est obligatoire")
        @Positive(message = "Le montant doit etre strictement positif")
        BigDecimal receivedAmountXof,

        @NotBlank(message = "La reference de transaction est obligatoire")
        @Size(max = 100)
        String transactionReference,

        @Size(max = 20)
        String payerPhone
) {
}
