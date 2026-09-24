package com.converter.business.reporting.service;

import com.converter.business.profile.service.BusinessProfileService;
import com.converter.business.reporting.dto.BusinessPaymentSummaryResponse;
import com.converter.business.reporting.dto.ReportPeriod;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.OrderStatus;
import com.converter.order.repository.OrderRepository;
import com.converter.order.repository.OrderStatusAggregate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vue consolidee, en lecture seule, des ordres d'un utilisateur Business — interroge {@code Order}
 * directement (via {@link OrderRepository#aggregateByStatus}, agregation SQL {@code GROUP BY},
 * jamais un chargement complet suivi d'une somme cote Java), jamais une table dupliquee : les
 * memes transactions servent aux particuliers et aux professionnels (section 32).
 *
 * <p><b>Verite financiere</b> : chaque montant agrege provient exclusivement des colonnes deja
 * figees d'{@code Order} (copie du {@code Quote} au moment de sa creation) — aucune dependance
 * vers {@code RateEngine}/{@code SettingsService}/tout pricing courant, aucun recalcul de taux,
 * de frais ou de break-even.
 *
 * <p><b>Reserve aux profils Business</b> (section 31) : {@code isBusinessUser(userId) == false} ->
 * {@code 404 BUSINESS_PROFILE_NOT_FOUND}, jamais {@code 403} — convention identique a toutes les
 * autres ressources non accessibles de ce backend.
 */
@Service
public class BusinessPaymentReportService {

    private final OrderRepository orderRepository;
    private final BusinessProfileService businessProfileService;

    public BusinessPaymentReportService(OrderRepository orderRepository,
                                        BusinessProfileService businessProfileService) {
        this.orderRepository = orderRepository;
        this.businessProfileService = businessProfileService;
    }

    @Transactional(readOnly = true)
    public BusinessPaymentSummaryResponse summary(UUID userId, Instant from, Instant to) {
        if (!businessProfileService.isBusinessUser(userId)) {
            throw new BusinessException(ErrorCode.BUSINESS_PROFILE_NOT_FOUND,
                    "Le reporting business necessite un profil professionnel.");
        }

        List<OrderStatusAggregate> rows = orderRepository.aggregateByStatus(userId, from != null, from, to != null, to);

        long transferCount = rows.stream().mapToLong(OrderStatusAggregate::count).sum();
        long completedCount = countFor(rows, OrderStatus.COMPLETED);
        long cancelledCount = countFor(rows, OrderStatus.CANCELLED);
        long rejectedCount = countFor(rows, OrderStatus.REJECTED);

        OrderStatusAggregate completed = rowFor(rows, OrderStatus.COMPLETED);
        BigDecimal totalAmountXof = completed == null ? BigDecimal.ZERO : completed.totalAmountXof();
        BigDecimal totalAmountCny = completed == null ? BigDecimal.ZERO : completed.totalAmountCny();
        BigDecimal totalFeesXof = completed == null ? BigDecimal.ZERO : completed.totalFeeXof();

        return new BusinessPaymentSummaryResponse(new ReportPeriod(from, to), transferCount, completedCount,
                cancelledCount, rejectedCount, totalAmountXof, totalAmountCny, totalFeesXof);
    }

    private static long countFor(List<OrderStatusAggregate> rows, OrderStatus status) {
        OrderStatusAggregate row = rowFor(rows, status);
        return row == null ? 0 : row.count();
    }

    private static OrderStatusAggregate rowFor(List<OrderStatusAggregate> rows, OrderStatus status) {
        return rows.stream().filter(row -> row.status() == status).findFirst().orElse(null);
    }
}
