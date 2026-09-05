package com.converter.order.repository;

import com.converter.order.domain.OrderStatus;

import java.math.BigDecimal;

/**
 * Projection d'agregation SQL (une ligne par {@code status}) — jamais un chargement des ordres
 * suivi d'une somme cote Java (section 27/32 de la specification Phase 8). Produite exclusivement
 * par {@link OrderRepository#aggregateByStatus}, consommee par
 * {@code BusinessPaymentReportService}.
 */
public record OrderStatusAggregate(OrderStatus status, long count, BigDecimal totalAmountXof,
                                   BigDecimal totalAmountCny, BigDecimal totalFeeXof) {
}
