package com.converter.order.service;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Table de transitions autorisees de l'ordre — composant unique et
 * centralise, comme documente dans l'architecture. Aucun endpoint,
 * aucun service n'a de moyen de faire passer un ordre par un statut
 * qui ne figure pas ici.
 *
 * <pre>
 * AWAITING_PAYMENT  -&gt; PAYMENT_SUBMITTED | CANCELLED | EXPIRED
 * PAYMENT_SUBMITTED -&gt; PAYMENT_VERIFIED | REJECTED
 * PAYMENT_VERIFIED  -&gt; PROCESSING
 * PROCESSING        -&gt; COMPLETED
 * </pre>
 *
 * Etats terminaux : COMPLETED, CANCELLED, REJECTED, EXPIRED.
 */
@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = buildTransitions();

    private static Map<OrderStatus, Set<OrderStatus>> buildTransitions() {
        Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);
        map.put(OrderStatus.AWAITING_PAYMENT,
                EnumSet.of(OrderStatus.PAYMENT_SUBMITTED, OrderStatus.CANCELLED, OrderStatus.EXPIRED));
        map.put(OrderStatus.PAYMENT_SUBMITTED,
                EnumSet.of(OrderStatus.PAYMENT_VERIFIED, OrderStatus.REJECTED));
        map.put(OrderStatus.PAYMENT_VERIFIED, EnumSet.of(OrderStatus.PROCESSING));
        map.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.COMPLETED));
        map.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        map.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        map.put(OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class));
        map.put(OrderStatus.EXPIRED, EnumSet.noneOf(OrderStatus.class));
        return map;
    }

    /** @throws BusinessException {@code INVALID_ORDER_STATE} si la transition n'est pas legale */
    public void assertTransition(OrderStatus from, OrderStatus to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE,
                    "Transition invalide : " + from + " -> " + to + ".");
        }
    }
}
