package com.converter.business.reporting.dto;

import java.math.BigDecimal;

/**
 * {@code transferCount}/{@code completedCount}/{@code cancelledCount}/{@code rejectedCount}
 * portent sur <b>tous les ordres du perimetre</b> (jamais les {@code Refund}, jamais confondus
 * avec un transfert supplementaire — section 22/23 de la specification).
 *
 * <p>{@code totalAmountXof}/{@code totalAmountCny}/{@code totalFeesXof} ne somment en revanche
 * que les ordres {@code COMPLETED} : seul un ordre termine represente un mouvement d'argent
 * reellement realise ; inclure un ordre {@code CANCELLED}/{@code REJECTED} dans ces totaux
 * gonflerait artificiellement un "volume traite" qui n'a jamais eu lieu. Convention documentee
 * explicitement ici et dans {@code docs/ARCHITECTURE.md} (la specification ne la fixait pas).
 */
public record BusinessPaymentSummaryResponse(
        ReportPeriod period,
        long transferCount,
        long completedCount,
        long cancelledCount,
        long rejectedCount,
        BigDecimal totalAmountXof,
        BigDecimal totalAmountCny,
        BigDecimal totalFeesXof
) {
}
