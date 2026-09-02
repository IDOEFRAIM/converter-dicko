package com.converter.settlement.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.Beneficiary;
import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.order.service.OrderService;
import com.converter.settlement.domain.Settlement;
import com.converter.settlement.domain.SettlementProof;
import com.converter.settlement.domain.SettlementStatus;
import com.converter.settlement.dto.SettlementProofResponse;
import com.converter.settlement.dto.SettlementResponse;
import com.converter.settlement.repository.SettlementProofRepository;
import com.converter.settlement.repository.SettlementRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestration de l'execution manuelle du reglement CNY.
 *
 * <p>{@code settlement} depend de {@code order} (declenche ses
 * transitions via {@link OrderService}) et de {@code treasury}
 * (consommation de la reservation a l'execution). Aucune integration
 * Binance/OKX/P2P/API chinoise : le champ {@code settlementReference}
 * est saisi manuellement par l'operateur qui a execute le virement.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);
    private static final String PROOF_DIRECTORY = "settlement-proofs";

    private final SettlementRepository settlementRepository;
    private final SettlementProofRepository proofRepository;
    private final OrderService orderService;
    private final TreasuryService treasuryService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;
    private final FileValidator fileValidator;
    private final Clock clock;

    public SettlementService(SettlementRepository settlementRepository,
                             SettlementProofRepository proofRepository,
                             OrderService orderService,
                             TreasuryService treasuryService,
                             SettingsService settingsService,
                             AuditService auditService,
                             FileStorageService fileStorageService,
                             FileValidator fileValidator,
                             Clock clock) {
        this.settlementRepository = settlementRepository;
        this.proofRepository = proofRepository;
        this.orderService = orderService;
        this.treasuryService = treasuryService;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
        this.clock = clock;
    }

    /**
     * Cree le reglement pour un ordre dont le paiement vient d'etre
     * confirme, et fait passer l'ordre en PROCESSING dans la meme
     * transaction : "Payment CONFIRMED -&gt; Settlement cree" est ainsi
     * une seule operation atomique.
     */
    @Transactional
    public SettlementResponse create(UUID orderId, UUID actorId) {
        Order order = orderService.getEntityOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PAYMENT_VERIFIED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE,
                    "Le reglement ne peut etre cree que depuis PAYMENT_VERIFIED (statut actuel : "
                            + order.getStatus() + ").");
        }
        if (settlementRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_STATE,
                    "Un reglement existe deja pour cet ordre.");
        }

        Beneficiary beneficiary = orderService.getBeneficiaryOrThrow(orderId);
        Instant now = clock.instant();
        Settlement settlement = new Settlement(orderId, order.getAmountCny(), beneficiary.getType(),
                beneficiary.getFullName(), beneficiary.getIdentifier(), beneficiary.getBankName(),
                beneficiary.getBankBranch(), now);
        Settlement saved;
        try {
            saved = settlementRepository.saveAndFlush(settlement);
        } catch (DataIntegrityViolationException ex) {
            // uq_settlements_order : deux creations concurrentes ; l'une gagne. Code metier
            // precis plutot que le DUPLICATE_RESOURCE generique (passe 2, P2-6).
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_STATE,
                    "Un reglement existe deja pour cet ordre.");
        }

        orderService.transitionToProcessing(orderId, actorId);

        auditService.record(actorId, null, AuditAction.SETTLEMENT_CREATED, "Settlement",
                saved.getId().toString(), "{\"orderId\":\"" + orderId + "\"}");
        log.info("Reglement {} cree pour l'ordre {} par {}", saved.getId(), orderId, actorId);

        return toResponse(saved, List.of());
    }

    @Transactional
    public SettlementResponse execute(UUID settlementId, String reference, String notes, UUID actorId) {
        Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> notFound(settlementId));

        if (settingsService.getBoolean(SettingKey.REQUIRE_PAYMENT_PROOF)
                && proofRepository.countBySettlementId(settlementId) == 0) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_PROOF,
                    "Au moins une preuve de reglement est requise avant execution.");
        }

        Instant now = clock.instant();
        settlement.execute(reference, notes, actorId, now);
        settlementRepository.save(settlement);

        // La consommation de tresorerie ne doit jamais s'appliquer a un ordre qui n'a pas
        // reellement ete reserve (ex. TREASURY_RESERVE_ON_ORDER desactive au moment de la
        // creation de l'ordre) : reserved_balance est un solde agrege par devise, partage entre
        // tous les ordres -- consommer sans reservation correspondante reviendrait a emprunter
        // silencieusement sur la reservation d'un autre ordre. Miroir exact du garde-fou deja
        // applique cote liberation par OrderService.releaseReservationIfNeeded.
        Order order = orderService.getEntityOrThrow(settlement.getOrderId());
        if (order.isTreasuryReserved()) {
            treasuryService.consume(Currency.CNY, settlement.getAmountCny(), settlement.getOrderId(), actorId);
        } else {
            log.warn("Reglement {} execute pour l'ordre {} sans reservation de tresorerie prealable "
                            + "(TREASURY_RESERVE_ON_ORDER etait desactive a la creation) : aucune consommation enregistree.",
                    settlementId, settlement.getOrderId());
        }
        orderService.transitionToCompleted(settlement.getOrderId(), actorId);

        auditService.record(actorId, null, AuditAction.SETTLEMENT_EXECUTED, "Settlement",
                settlementId.toString(), "{\"reference\":\"" + reference + "\"}");
        log.info("Reglement {} execute par {} (reference {})", settlementId, actorId, reference);

        return toResponse(settlement, loadProofs(settlementId));
    }

    @Transactional
    public SettlementProofResponse uploadProof(UUID settlementId, String originalFileName,
                                               String declaredContentType, byte[] content, UUID actorId) {
        Settlement settlement = settlementRepository.findById(settlementId).orElseThrow(() -> notFound(settlementId));
        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_STATE,
                    "Ce reglement n'accepte plus de nouvelle preuve (statut actuel : " + settlement.getStatus() + ").");
        }

        long maxSize = settingsService.getLong(SettingKey.MAX_PROOF_FILE_SIZE_BYTES);
        fileValidator.validate(declaredContentType, content, maxSize);

        StoredFile stored = fileStorageService.store(PROOF_DIRECTORY, originalFileName, declaredContentType, content);
        Instant now = clock.instant();
        SettlementProof proof = new SettlementProof(settlementId, stored.fileName(), stored.contentType(),
                stored.storageKey(), "LOCAL", stored.sizeBytes(), stored.checksumSha256(), actorId, now);
        SettlementProof saved = proofRepository.save(proof);

        return toProofResponse(saved);
    }

    @Transactional(readOnly = true)
    public Resource downloadProof(UUID settlementId, UUID proofId) {
        List<SettlementProof> proofs = proofRepository.findBySettlementIdOrderByUploadedAtAsc(settlementId);
        SettlementProof proof = proofs.stream()
                .filter(p -> p.getId().equals(proofId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Preuve introuvable : " + proofId));
        return fileStorageService.load(proof.getStorageKey());
    }

    @Transactional(readOnly = true)
    public SettlementResponse get(UUID settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId).orElseThrow(() -> notFound(settlementId));
        return toResponse(settlement, loadProofs(settlementId));
    }

    @Transactional(readOnly = true)
    public SettlementResponse getByOrder(UUID orderId) {
        Settlement settlement = settlementRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND,
                        "Aucun reglement pour l'ordre " + orderId));
        return toResponse(settlement, loadProofs(settlement.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<SettlementResponse> pending(Pageable pageable) {
        Page<Settlement> page = settlementRepository.findByStatusOrderByCreatedAtAsc(SettlementStatus.PENDING, pageable);
        return PageResponse.from(page, s -> toResponse(s, loadProofs(s.getId())));
    }

    // -----------------------------------------------------------------

    private List<SettlementProofResponse> loadProofs(UUID settlementId) {
        return proofRepository.findBySettlementIdOrderByUploadedAtAsc(settlementId).stream()
                .map(SettlementService::toProofResponse)
                .toList();
    }

    private BusinessException notFound(UUID settlementId) {
        return new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND, "Reglement introuvable : " + settlementId);
    }

    private static SettlementProofResponse toProofResponse(SettlementProof proof) {
        return new SettlementProofResponse(proof.getId(), proof.getFileName(), proof.getContentType(),
                proof.getSizeBytes(), proof.getUploadedAt());
    }

    private static SettlementResponse toResponse(Settlement settlement, List<SettlementProofResponse> proofs) {
        return new SettlementResponse(
                settlement.getId(), settlement.getOrderId(), settlement.getStatus(), settlement.getAmountCny(),
                settlement.getMethod(), settlement.getBeneficiaryFullName(), settlement.getBeneficiaryIdentifier(),
                settlement.getBeneficiaryBankName(), settlement.getBeneficiaryBankBranch(),
                settlement.getSettlementReference(), settlement.getNotes(), settlement.getExecutedBy(), proofs,
                settlement.getCreatedAt(), settlement.getExecutedAt());
    }
}
