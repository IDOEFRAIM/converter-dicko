package com.converter.order.service;

import com.converter.common.api.PageResponse;
import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.order.dto.OrderHistoryResponse;
import com.converter.order.repository.OrderRepository;
import com.converter.supplier.domain.Purpose;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Recherche enrichie, en lecture seule, de l'historique des ordres d'un utilisateur (Phase 8) —
 * ouverte a tout utilisateur authentifie (Personal comme Business, voir la regle "ONE financial
 * core, MULTIPLE customer experiences" de la mission), jamais reservee aux profils Business.
 *
 * <p>Meme separation que {@code OrderTrackingService} : une simple projection en lecture seule
 * au-dessus d'{@code Order}, aucune mutation, aucune seconde source de verite. Toujours filtre par
 * {@code userId} en premier — un {@code supplierId} appartenant a un autre utilisateur ne renvoie
 * jamais les donnees de ce dernier, simplement aucun resultat (voir {@code OrderRepository#searchHistory}).
 *
 * <p>Tri toujours {@code createdAt DESC, id DESC}, non negociable par l'appelant : seuls le numero
 * et la taille de page d'un {@code Pageable} fourni sont retenus, tout tri qu'il porterait est
 * ignore (meme convention que {@code PublicRateSnapshotService}/{@code RateAlertService}).
 */
@Service
public class OrderHistoryService {

    private final OrderRepository orderRepository;

    public OrderHistoryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderHistoryResponse> search(UUID userId, OrderStatus status, Purpose purpose,
                                                      UUID supplierId, Instant from, Instant to, Pageable pageable) {
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<Order> page = orderRepository.searchHistory(userId, status != null, status, purpose != null, purpose,
                supplierId != null, supplierId, from != null, from, to != null, to, pageOnly);
        return PageResponse.from(page, OrderHistoryService::toResponse);
    }

    private static OrderHistoryResponse toResponse(Order order) {
        return new OrderHistoryResponse(order.getId(), order.getReference(), order.getStatus(),
                order.getAmountXof(), order.getAmountCny(), order.getFeeXof(), order.getCustomerRate(),
                order.getPurpose(), order.getSupplierId(), order.getCreatedAt(), order.getCompletedAt());
    }
}
