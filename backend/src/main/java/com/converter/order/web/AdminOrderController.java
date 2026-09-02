package com.converter.order.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.order.domain.OrderStatus;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.order.dto.OrderSummaryResponse;
import com.converter.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Consultation des ordres, tous clients confondus, reservee a l'administration. */
@RestController
@RequestMapping("/api/admin/orders")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Ordres", description = "Consultation des ordres, tous clients confondus")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Lister les ordres", description = "Filtrable par statut.")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> list(
            @RequestParam(required = false) OrderStatus status,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(orderService.adminList(status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail complet d'un ordre")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.adminGet(id)));
    }
}
