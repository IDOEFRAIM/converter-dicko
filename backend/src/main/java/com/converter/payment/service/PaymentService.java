package com.converter.payment.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.common.api.PageResponse;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.service.NotificationService;
import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.order.service.OrderService;
import com.converter.payment.domain.Payment;
import com.converter.payment.domain.PaymentMethod;
import com.converter.payment.domain.PaymentProof;
import com.converter.payment.domain.PaymentStatus;
import com.converter.payment.dto.PaymentProofResponse;
import com.converter.payment.dto.PaymentResponse;
import com.converter.payment.dto.SubmitPaymentRequest;
import com.converter.payment.repository.PaymentProofRepository;
import com.converter.payment.repository.PaymentRepository;
import com.converter.security.OwnershipService;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import com.converter.storage.FileStorageService;
import com.converter.storage.FileValidator;
import com.converter.storage.StoredFile;
import com.converter.storage.exception.InvalidFileException;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.service.TreasuryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestration de la declaration et de la revue d'un paiement XOF.
 *
 * <p>MVP entierement manuel : aucune integration Mobile Money, banque
 * ou Wave. Le client declare avoir paye hors plateforme ; un
 * administrateur confirme ou rejette apres controle de la preuve.
 * {@code payment} depend de {@code order} (declenche ses transitions
 * via {@link OrderService}, jamais en modifiant {@code Order}
 * directement) et de {@code treasury} (encaissement XOF a la
 * confirmation).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String PROOF_DIRECTORY = "payment-proofs";

    private final PaymentRepository paymentRepository;
    private final PaymentProofRepository proofRepository;
    private final OrderService orderService;
    private final SettingsService settingsService;
    private final OwnershipService ownershipService;
    private final AuditService auditService;
    private final TreasuryService treasuryService;
    private final FileStorageService fileStorageService;
    private final FileValidator fileValidator;
    private final NotificationService notificationService;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentProofRepository proofRepository,
                          OrderService orderService,
                          SettingsService settingsService,
                          OwnershipService ownershipService,
                          AuditService auditService,
                          TreasuryService treasuryService,
                          FileStorageService fileStorageService,
                          FileValidator fileValidator,
                          NotificationService notificationService,
                          Clock clock) {
        this.paymentRepository = paymentRepository;
        this.proofRepository = proofRepository;
        this.orderService = orderService;
        this.settingsService = settingsService;
        this.ownershipService = ownershipService;
        this.auditService = auditService;
        this.treasuryService = treasuryService;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    // -----------------------------------------------------------------
    // Client
    // -----------------------------------------------------------------

    @Transactional
    public PaymentResponse submit(UUID orderId, SubmitPaymentRequest request, UUID userId) {
        Order order = orderService.getEntityOrThrow(orderId);
        ownershipService.assertOwnedBy(order.getUserId(), userId, ErrorCode.ORDER_NOT_FOUND,
                "Ordre introuvable : " + orderId);

        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE,
                    "Cet ordre n'est plus en attente de paiement (statut actuel : " + order.getStatus() + ").");
        }
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE,
                    "Un paiement a deja ete declare pour cet ordre.");
        }
        assertMethodEnabled(request.method());
        assertAmountMatchesExpected(order.getAmountXof(), request.receivedAmountXof());
        if (paymentRepository.existsByMethodAndTransactionReference(request.method(), request.transactionReference())) {
            throw new BusinessException(ErrorCode.DUPLICATE_TRANSACTION_REFERENCE,
                    "Cette reference de transaction a deja ete utilisee pour un autre paiement.");
        }

        Instant now = clock.instant();
        Payment payment = new Payment(orderId, request.method(), order.getAmountXof(),
                request.receivedAmountXof(), request.transactionReference(), request.payerPhone(), now);
        Payment saved;
        try {
            saved = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {
            // Course concurrente ayant franchi les pre-verifications ci-dessus (uq_payments_order
            // ou uq_payments_txref). Impossible de re-interroger la base ici (la transaction PG
            // est avortee par la violation) ; le cas concurrent dominant est de tres loin la
            // double declaration pour le meme ordre. On renvoie donc le code metier precis
            // correspondant, jamais le DUPLICATE_RESOURCE generique (passe 2, P2-6).
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE,
                    "Un paiement a deja ete declare pour cet ordre (ou la reference de transaction est deja prise).");
        }

        // Transition de l'ordre dans la MEME transaction : soit les deux
        // ecritures reussissent, soit aucune (propagation REQUIRED par defaut).
        orderService.transitionToPaymentSubmitted(orderId, userId);

        auditService.record(userId, null, AuditAction.PAYMENT_SUBMITTED, "Payment", saved.getId().toString(),
                "{\"orderId\":\"" + orderId + "\",\"reference\":\"" + request.transactionReference() + "\"}");
        notificationService.create(userId, NotificationType.PAYMENT_SUBMITTED, "Paiement declare",
                "Votre paiement pour l'ordre " + orderId + " a ete declare et est en cours de verification.");
        log.info("Paiement {} declare pour l'ordre {} par {}", saved.getId(), orderId, userId);

        return toResponse(saved, List.of());
    }

    @Transactional
    public PaymentProofResponse uploadProof(UUID paymentId, String originalFileName, String declaredContentType,
                                            byte[] content, UUID userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> notFoundPayment(paymentId));
        Order order = orderService.getEntityOrThrow(payment.getOrderId());
        ownershipService.assertOwnedBy(order.getUserId(), userId, ErrorCode.PAYMENT_NOT_FOUND,
                "Paiement introuvable : " + paymentId);

        if (payment.getStatus() != PaymentStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE,
                    "Ce paiement n'accepte plus de nouvelle preuve (statut actuel : " + payment.getStatus() + ").");
        }
        int maxProofs = settingsService.getInt(SettingKey.MAX_PROOFS_PER_PAYMENT);
        if (proofRepository.countByPaymentId(paymentId) >= maxProofs) {
            throw new InvalidFileException("Nombre maximal de preuves atteint pour ce paiement (" + maxProofs + ").");
        }

        long maxSize = settingsService.getLong(SettingKey.MAX_PROOF_FILE_SIZE_BYTES);
        fileValidator.validate(declaredContentType, content, maxSize);

        StoredFile stored = fileStorageService.store(PROOF_DIRECTORY, originalFileName, declaredContentType, content);
        Instant now = clock.instant();
        PaymentProof proof = new PaymentProof(paymentId, stored.fileName(), stored.contentType(),
                stored.storageKey(), "LOCAL", stored.sizeBytes(), stored.checksumSha256(), userId, now);
        PaymentProof saved = proofRepository.save(proof);

        auditService.record(userId, null, AuditAction.PAYMENT_PROOF_UPLOADED, "Payment", paymentId.toString(),
                "{\"proofId\":\"" + saved.getId() + "\"}");

        return toProofResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId, UUID userId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> notFoundPayment(paymentId));
        Order order = orderService.getEntityOrThrow(payment.getOrderId());
        ownershipService.assertOwnedBy(order.getUserId(), userId, ErrorCode.PAYMENT_NOT_FOUND,
                "Paiement introuvable : " + paymentId);
        return toResponse(payment, loadProofs(paymentId));
    }

    @Transactional(readOnly = true)
    public Resource downloadProof(UUID paymentId, UUID proofId, UUID userId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> notFoundPayment(paymentId));
        Order order = orderService.getEntityOrThrow(payment.getOrderId());
        ownershipService.assertOwnedBy(order.getUserId(), userId, ErrorCode.PAYMENT_NOT_FOUND,
                "Paiement introuvable : " + paymentId);
        PaymentProof proof = proofRepository.findByPaymentIdOrderByUploadedAtAsc(paymentId).stream()
                .filter(p -> p.getId().equals(proofId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Preuve introuvable : " + proofId));
        return fileStorageService.load(proof.getStorageKey());
    }

    // -----------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public Resource adminDownloadProof(UUID paymentId, UUID proofId) {
        PaymentProof proof = proofRepository.findByPaymentIdOrderByUploadedAtAsc(paymentId).stream()
                .filter(p -> p.getId().equals(proofId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Preuve introuvable : " + proofId));
        return fileStorageService.load(proof.getStorageKey());
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> pending(Pageable pageable) {
        Page<Payment> page = paymentRepository.findByStatusOrderBySubmittedAtAsc(PaymentStatus.SUBMITTED, pageable);
        return PageResponse.from(page, p -> toResponse(p, loadProofs(p.getId())));
    }

    @Transactional(readOnly = true)
    public PaymentResponse adminGet(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> notFoundPayment(paymentId));
        return toResponse(payment, loadProofs(paymentId));
    }

    @Transactional
    public PaymentResponse confirm(UUID paymentId, UUID actorId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(() -> notFoundPayment(paymentId));

        if (settingsService.getBoolean(SettingKey.REQUIRE_PAYMENT_PROOF) && proofRepository.countByPaymentId(paymentId) == 0) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_PROOF,
                    "Au moins une preuve de paiement est requise avant confirmation.");
        }

        Instant now = clock.instant();
        payment.confirm(actorId, now);
        paymentRepository.save(payment);

        orderService.transitionToPaymentVerified(payment.getOrderId(), actorId);
        treasuryService.deposit(Currency.XOF, payment.getReceivedAmountXof(), actorId,
                "Paiement confirme, ordre " + payment.getOrderId());

        auditService.record(actorId, null, AuditAction.PAYMENT_CONFIRMED, "Payment", paymentId.toString(), null);
        Order order = orderService.getEntityOrThrow(payment.getOrderId());
        notificationService.create(order.getUserId(), NotificationType.PAYMENT_CONFIRMED, "Paiement confirme",
                "Votre paiement pour l'ordre " + payment.getOrderId() + " a ete confirme.");
        log.info("Paiement {} confirme par {}", paymentId, actorId);

        return toResponse(payment, loadProofs(paymentId));
    }

    @Transactional
    public PaymentResponse reject(UUID paymentId, String reason, UUID actorId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(() -> notFoundPayment(paymentId));

        Instant now = clock.instant();
        payment.reject(actorId, reason, now);
        paymentRepository.save(payment);

        orderService.transitionToRejected(payment.getOrderId(), actorId, reason);

        auditService.record(actorId, null, AuditAction.PAYMENT_REJECTED, "Payment", paymentId.toString(),
                "{\"reason\":" + jsonString(reason) + "}");
        log.info("Paiement {} rejete par {} : {}", paymentId, actorId, reason);

        return toResponse(payment, loadProofs(paymentId));
    }

    // -----------------------------------------------------------------

    private void assertMethodEnabled(PaymentMethod method) {
        List<String> enabled = settingsService.getList(SettingKey.ENABLED_PAYMENT_METHODS);
        if (!enabled.contains(method.name())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La methode " + method + " n'est pas activee. Methodes actives : " + enabled + ".");
        }
    }

    /**
     * Le montant XOF declare recu doit correspondre au montant attendu de l'ordre, a la tolerance
     * pres ({@code PAYMENT_AMOUNT_TOLERANCE_XOF}, defaut 0 = exact). Sous-paiement comme
     * sur-paiement au-dela de la tolerance sont rejetes : le flux est manuel, le client doit
     * payer le montant exact — un sous-paiement accepte serait une perte de change directe pour
     * la plateforme (le decaissement CNY reste calcule sur le montant de l'ordre). Passe 2, P2-2.
     */
    private void assertAmountMatchesExpected(BigDecimal expectedXof, BigDecimal receivedXof) {
        BigDecimal tolerance = settingsService.getDecimal(SettingKey.PAYMENT_AMOUNT_TOLERANCE_XOF);
        if (receivedXof.subtract(expectedXof).abs().compareTo(tolerance) > 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "Le montant declare (" + receivedXof + " XOF) doit correspondre au montant attendu ("
                            + expectedXof + " XOF)"
                            + (tolerance.signum() > 0 ? " a " + tolerance + " XOF pres." : "."));
        }
    }

    private List<PaymentProofResponse> loadProofs(UUID paymentId) {
        return proofRepository.findByPaymentIdOrderByUploadedAtAsc(paymentId).stream()
                .map(PaymentService::toProofResponse)
                .toList();
    }

    private BusinessException notFoundPayment(UUID paymentId) {
        return new BusinessException(ErrorCode.PAYMENT_NOT_FOUND, "Paiement introuvable : " + paymentId);
    }

    private static String jsonString(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static PaymentProofResponse toProofResponse(PaymentProof proof) {
        return new PaymentProofResponse(proof.getId(), proof.getFileName(), proof.getContentType(),
                proof.getSizeBytes(), proof.getUploadedAt());
    }

    private static PaymentResponse toResponse(Payment payment, List<PaymentProofResponse> proofs) {
        return new PaymentResponse(
                payment.getId(), payment.getOrderId(), payment.getMethod(), payment.getStatus(),
                payment.getExpectedAmountXof(), payment.getReceivedAmountXof(), payment.getTransactionReference(),
                payment.getPayerPhone(), payment.getRejectionReason(), proofs,
                payment.getSubmittedAt(), payment.getConfirmedAt(), payment.getRejectedAt());
    }
}
