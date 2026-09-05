package com.converter.order.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.common.idempotency.IdempotencyGuard;
import com.converter.order.domain.OrderStatus;
import com.converter.order.dto.CancelOrderRequest;
import com.converter.order.dto.CreateOrderRequest;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.order.dto.OrderFeasibilityResponse;
import com.converter.order.dto.OrderHistoryResponse;
import com.converter.order.dto.OrderSummaryResponse;
import com.converter.order.dto.OrderTrackingResponse;
import com.converter.order.receipt.model.ReceiptDocument;
import com.converter.order.receipt.service.OrderReceiptService;
import com.converter.order.service.OrderHistoryService;
import com.converter.order.service.OrderService;
import com.converter.order.service.OrderTrackingService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.supplier.domain.Purpose;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Cycle de vie client d'un ordre. Un ordre n'est jamais cree
 * directement a partir d'un montant : il reference exclusivement un
 * {@code Quote} deja accepte (voir {@code OrderService}). Ownership
 * appliquee comme partout ailleurs : 404, jamais 403.
 */
@RestController
@RequestMapping("/api/v1/orders")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Ordres", description = "Creation et suivi des ordres client")
public class OrderController {

    private static final String CREATE_ENDPOINT = "POST /api/v1/orders";

    private final OrderService orderService;
    private final OrderTrackingService orderTrackingService;
    private final OrderReceiptService orderReceiptService;
    private final OrderHistoryService orderHistoryService;
    private final IdempotencyGuard idempotencyGuard;

    public OrderController(OrderService orderService, OrderTrackingService orderTrackingService,
                           OrderReceiptService orderReceiptService, OrderHistoryService orderHistoryService,
                           IdempotencyGuard idempotencyGuard) {
        this.orderService = orderService;
        this.orderTrackingService = orderTrackingService;
        this.orderReceiptService = orderReceiptService;
        this.orderHistoryService = orderHistoryService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @PostMapping
    @Operation(summary = "Creer un ordre a partir d'un devis accepte",
            description = "Le devis doit appartenir au client et etre au statut ACCEPTED. "
                    + "Un devis ne peut produire qu'un seul ordre. En-tete Idempotency-Key "
                    + "optionnel : un rejeu (timeout, double clic) avec la meme cle et le meme "
                    + "corps ne cree jamais un second ordre, la reponse d'origine est rejouee.")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticatedUser CurrentUser currentUser) {
        return idempotencyGuard.guard(currentUser.getId(), CREATE_ENDPOINT, idempotencyKey, request,
                new TypeReference<ApiResponse<OrderDetailResponse>>() {
                },
                () -> {
                    OrderDetailResponse response = orderService.create(request, currentUser.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Ordre cree."));
                });
    }

    @GetMapping
    @Operation(summary = "Lister mes ordres")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> list(
            @AuthenticatedUser CurrentUser currentUser,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(orderService.listMine(currentUser.getId(), pageable)));
    }

    @GetMapping("/history")
    @Operation(summary = "Historique enrichi de mes ordres",
            description = "Disponible a tout utilisateur authentifie (Personal comme Business) -- meme pipeline "
                    + "financier pour les deux. Filtres optionnels : status, purpose, supplierId, from, to "
                    + "(bornes Instant, from <= createdAt < to). Un supplierId d'un autre utilisateur ne renvoie "
                    + "jamais ses donnees, uniquement aucun resultat. Tri fixe createdAt DESC, id DESC.")
    public ResponseEntity<ApiResponse<PageResponse<OrderHistoryResponse>>> history(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Purpose purpose,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticatedUser CurrentUser currentUser,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(orderHistoryService.search(currentUser.getId(), status, purpose,
                supplierId, from, to, pageable)));
    }

    @GetMapping("/feasibility")
    @Operation(summary = "Verifier qu'un ordre est realisable a partir d'un devis",
            description = "A appeler AVANT la saisie du beneficiaire : indique si le devis est bien "
                    + "accepte et appartient au client, et si la liquidite CNY disponible couvre "
                    + "actuellement son montant. N'expose aucun solde de tresorerie. Indicateur "
                    + "d'affichage : la verite reste la reservation faite a la creation de l'ordre.")
    public ResponseEntity<ApiResponse<OrderFeasibilityResponse>> feasibility(
            @RequestParam UUID quoteId,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(orderService.checkFeasibility(quoteId, currentUser.getId())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'un ordre")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> get(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(orderService.get(id, currentUser.getId())));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler un ordre",
            description = "Uniquement depuis AWAITING_PAYMENT. Libere la reservation de tresorerie.")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelOrderRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        OrderDetailResponse response = orderService.cancel(id, currentUser.getId(), request.reason());
        return ResponseEntity.ok(ApiResponse.of(response, "Ordre annule."));
    }

    @GetMapping("/{id}/tracking")
    @Operation(summary = "Timeline de suivi d'un ordre",
            description = "Vue agregee en lecture seule de la progression de l'ordre — aucune mutation, "
                    + "aucune transition declenchee. Voir OrderTrackingService.")
    public ResponseEntity<ApiResponse<OrderTrackingResponse>> tracking(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(orderTrackingService.get(id, currentUser.getId())));
    }

    @GetMapping("/{id}/receipt")
    @Operation(summary = "Justificatif PDF d'une transaction",
            description = "Disponible uniquement pour un ordre termine (COMPLETED). Photographie documentaire "
                    + "des donnees deja figees de l'ordre -- aucun recalcul, aucune mutation.")
    public ResponseEntity<byte[]> receipt(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        ReceiptDocument document = orderReceiptService.generate(id, currentUser.getId());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(document.content());
    }
}
