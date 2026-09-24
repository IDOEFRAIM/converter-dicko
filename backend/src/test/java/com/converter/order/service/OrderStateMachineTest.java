package com.converter.order.service;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure, sans Spring : verifie la table de transitions dans son integralite. */
class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    void nominalPath_isEntirelyLegal() {
        stateMachine.assertTransition(OrderStatus.AWAITING_PAYMENT, OrderStatus.PAYMENT_SUBMITTED);
        stateMachine.assertTransition(OrderStatus.PAYMENT_SUBMITTED, OrderStatus.PAYMENT_VERIFIED);
        stateMachine.assertTransition(OrderStatus.PAYMENT_VERIFIED, OrderStatus.PROCESSING);
        stateMachine.assertTransition(OrderStatus.PROCESSING, OrderStatus.COMPLETED);
    }

    @Test
    void alternativePaths_areLegal() {
        stateMachine.assertTransition(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED);
        stateMachine.assertTransition(OrderStatus.AWAITING_PAYMENT, OrderStatus.EXPIRED);
        stateMachine.assertTransition(OrderStatus.PAYMENT_SUBMITTED, OrderStatus.REJECTED);
        stateMachine.assertTransition(OrderStatus.REJECTED, OrderStatus.PAYMENT_SUBMITTED);
    }

    /**
     * REJECTED n'est PLUS un etat terminal (retour client : forcer un nouvel ordre pour
     * resoumettre un paiement rejete etait un contournement, jamais une solution) : une
     * resoumission (REJECTED -&gt; PAYMENT_SUBMITTED) reste la SEULE transition legale depuis cet
     * etat, exclue ici du test generique de terminalite ci-dessous et verifiee separement.
     */
    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void terminalStates_acceptNoFurtherTransition(OrderStatus terminal) {
        if (terminal != OrderStatus.COMPLETED && terminal != OrderStatus.CANCELLED
                && terminal != OrderStatus.EXPIRED) {
            return;
        }
        for (OrderStatus target : OrderStatus.values()) {
            assertThatThrownBy(() -> stateMachine.assertTransition(terminal, target))
                    .as("%s -> %s doit etre refuse", terminal, target)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(ErrorCode.INVALID_ORDER_STATE));
        }
    }

    @Test
    void rejected_onlyAcceptsResubmissionToPaymentSubmitted() {
        stateMachine.assertTransition(OrderStatus.REJECTED, OrderStatus.PAYMENT_SUBMITTED);
        for (OrderStatus target : OrderStatus.values()) {
            if (target == OrderStatus.PAYMENT_SUBMITTED) {
                continue;
            }
            assertThatThrownBy(() -> stateMachine.assertTransition(OrderStatus.REJECTED, target))
                    .as("REJECTED -> %s doit etre refuse", target)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(ErrorCode.INVALID_ORDER_STATE));
        }
    }

    @Test
    void skippingAStep_isIllegal() {
        // AWAITING_PAYMENT ne peut pas sauter directement a PAYMENT_VERIFIED,
        // PROCESSING ou COMPLETED.
        assertThatThrownBy(() -> stateMachine.assertTransition(OrderStatus.AWAITING_PAYMENT, OrderStatus.PAYMENT_VERIFIED))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> stateMachine.assertTransition(OrderStatus.AWAITING_PAYMENT, OrderStatus.PROCESSING))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> stateMachine.assertTransition(OrderStatus.PAYMENT_SUBMITTED, OrderStatus.PROCESSING))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reverseTransition_isIllegal() {
        assertThatThrownBy(() -> stateMachine.assertTransition(OrderStatus.PAYMENT_VERIFIED, OrderStatus.PAYMENT_SUBMITTED))
                .isInstanceOf(BusinessException.class);
    }
}
