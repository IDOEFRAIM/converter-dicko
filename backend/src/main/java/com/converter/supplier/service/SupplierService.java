package com.converter.supplier.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.BeneficiaryType;
import com.converter.security.OwnershipService;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import com.converter.storage.FileStorageService;
import com.converter.storage.FileValidator;
import com.converter.storage.ProofDownload;
import com.converter.storage.StoredFile;
import com.converter.supplier.domain.Supplier;
import com.converter.supplier.domain.SupplierStatus;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.supplier.dto.SupplierSummaryResponse;
import com.converter.supplier.dto.UpdateSupplierRequest;
import com.converter.supplier.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Carnet de fournisseurs/beneficiaires reutilisables — orchestration CRUD, favori,
 * desactivation. Isolation stricte par proprietaire (voir {@link OwnershipService}).
 *
 * <p><b>Protection des donnees (section 18)</b> : aucune ligne de log de cette classe ne porte
 * jamais {@code accountNumber} en clair — uniquement l'identifiant du fournisseur et son
 * proprietaire, jamais une donnee bancaire/de paiement. Les reponses de liste masquent
 * {@code accountNumber} (voir {@link #mask(String)}) ; seule la consultation deliberee d'un
 * fournisseur precis ({@link #get}) le renvoie en clair a son proprietaire.
 */
@Service
public class SupplierService {

    private static final Logger log = LoggerFactory.getLogger(SupplierService.class);

    private static final String QR_CODE_DIRECTORY = "supplier-qr-codes";

    private final SupplierRepository supplierRepository;
    private final OwnershipService ownershipService;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;
    private final FileValidator fileValidator;
    private final SettingsService settingsService;

    public SupplierService(SupplierRepository supplierRepository,
                           OwnershipService ownershipService,
                           AuditService auditService,
                           FileStorageService fileStorageService,
                           FileValidator fileValidator,
                           SettingsService settingsService) {
        this.supplierRepository = supplierRepository;
        this.ownershipService = ownershipService;
        this.auditService = auditService;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
        this.settingsService = settingsService;
    }

    @Transactional
    public SupplierDetailResponse create(CreateSupplierRequest request, UUID ownerUserId) {
        Supplier supplier = new Supplier(ownerUserId, request.type(), request.displayName(), request.legalName(),
                request.phone(), request.email(), request.country(), request.city(), request.province(),
                request.bankName(), request.bankBranch(), request.accountName(), request.accountNumber(),
                request.bankAddress(), request.swiftCode(), request.currency(), request.purpose(), request.notes());
        Supplier saved = supplierRepository.save(supplier);

        auditService.record(ownerUserId, null, AuditAction.SUPPLIER_CREATED, "Supplier", saved.getId().toString(),
                null);
        log.info("Fournisseur {} cree par {}", saved.getId(), ownerUserId);

        return toDetail(saved);
    }

    @Transactional
    public SupplierDetailResponse update(UUID id, UpdateSupplierRequest request, UUID ownerUserId) {
        Supplier supplier = loadOwned(id, ownerUserId);

        supplier.update(request.type(), request.displayName(), request.legalName(), request.phone(),
                request.email(), request.country(), request.city(), request.province(), request.bankName(),
                request.bankBranch(), request.accountName(), request.accountNumber(), request.bankAddress(),
                request.swiftCode(), request.currency(), request.purpose(), request.notes());

        auditService.record(ownerUserId, null, AuditAction.SUPPLIER_UPDATED, "Supplier", id.toString(), null);
        log.info("Fournisseur {} mis a jour par {}", id, ownerUserId);

        return toDetail(supplier);
    }

    @Transactional(readOnly = true)
    public SupplierDetailResponse get(UUID id, UUID ownerUserId) {
        return toDetail(loadOwned(id, ownerUserId));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierSummaryResponse> list(UUID ownerUserId, SupplierStatus status, Pageable pageable) {
        Page<Supplier> page = status == null
                ? supplierRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId, pageable)
                : supplierRepository.findByOwnerUserIdAndStatusOrderByCreatedAtDesc(ownerUserId, status, pageable);
        return PageResponse.from(page, SupplierService::toSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierSummaryResponse> listFavorites(UUID ownerUserId, Pageable pageable) {
        return PageResponse.from(
                supplierRepository.findByOwnerUserIdAndFavoriteTrueOrderByCreatedAtDesc(ownerUserId, pageable),
                SupplierService::toSummary);
    }

    @Transactional
    public SupplierDetailResponse setFavorite(UUID id, UUID ownerUserId, boolean favorite) {
        Supplier supplier = loadOwned(id, ownerUserId);
        supplier.markFavorite(favorite);

        auditService.record(ownerUserId, null,
                favorite ? AuditAction.SUPPLIER_FAVORITED : AuditAction.SUPPLIER_UNFAVORITED,
                "Supplier", id.toString(), null);

        return toDetail(supplier);
    }

    @Transactional
    public SupplierDetailResponse deactivate(UUID id, UUID ownerUserId) {
        Supplier supplier = loadOwned(id, ownerUserId);
        supplier.deactivate();

        auditService.record(ownerUserId, null, AuditAction.SUPPLIER_DEACTIVATED, "Supplier", id.toString(), null);
        log.info("Fournisseur {} desactive par {}", id, ownerUserId);

        return toDetail(supplier);
    }

    /** Lecture directe pour le futur orchestrateur "payer a nouveau" (Phase 4) — jamais expose tel quel a un tiers. */
    @Transactional(readOnly = true)
    public Supplier getEntityOwned(UUID id, UUID ownerUserId) {
        return loadOwned(id, ownerUserId);
    }

    /**
     * Televerse (ou remplace) le code QR Alipay/WeChat d'un fournisseur — jamais un champ texte
     * (voir {@link Supplier#attachQrCode}). Un remplacement n'efface jamais l'ancien fichier :
     * un {@code Beneficiary} deja cree a partir de ce fournisseur peut encore le referencer.
     */
    @Transactional
    public SupplierDetailResponse attachQrCode(UUID id, String originalFileName, String declaredContentType,
                                               byte[] content, UUID ownerUserId) {
        Supplier supplier = loadOwned(id, ownerUserId);
        if (supplier.getType() == BeneficiaryType.CHINESE_BANK_ACCOUNT) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Un compte bancaire chinois n'utilise pas de code QR (identifiant textuel uniquement).");
        }

        long maxSize = settingsService.getLong(SettingKey.MAX_PROOF_FILE_SIZE_BYTES);
        fileValidator.validate(declaredContentType, content, maxSize);

        StoredFile stored = fileStorageService.store(QR_CODE_DIRECTORY, originalFileName, declaredContentType, content);
        supplier.attachQrCode(stored.storageKey(), stored.fileName(), stored.contentType(), stored.sizeBytes());

        auditService.record(ownerUserId, null, AuditAction.SUPPLIER_UPDATED, "Supplier", id.toString(),
                "{\"qrCodeAttached\":true}");
        log.info("Code QR televerse pour le fournisseur {} par {}", id, ownerUserId);

        return toDetail(supplier);
    }

    @Transactional(readOnly = true)
    public ProofDownload getQrCode(UUID id, UUID ownerUserId) {
        Supplier supplier = loadOwned(id, ownerUserId);
        if (supplier.getQrCodeStorageKey() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Ce fournisseur n'a pas encore de code QR televerse.");
        }
        return new ProofDownload(fileStorageService.load(supplier.getQrCodeStorageKey()),
                supplier.getQrCodeContentType(), supplier.getQrCodeFileName());
    }

    // -----------------------------------------------------------------

    private Supplier loadOwned(UUID id, UUID ownerUserId) {
        Supplier supplier = supplierRepository.findById(id).orElseThrow(() -> notFound(id));
        ownershipService.assertOwnedBy(supplier.getOwnerUserId(), ownerUserId, ErrorCode.SUPPLIER_NOT_FOUND,
                "Fournisseur introuvable : " + id);
        return supplier;
    }

    private static BusinessException notFound(UUID id) {
        return new BusinessException(ErrorCode.SUPPLIER_NOT_FOUND, "Fournisseur introuvable : " + id);
    }

    private static SupplierDetailResponse toDetail(Supplier s) {
        return new SupplierDetailResponse(
                s.getId(), s.getType(), s.getDisplayName(), s.getLegalName(), s.getPhone(), s.getEmail(),
                s.getCountry(), s.getCity(), s.getProvince(), s.getBankName(), s.getBankBranch(),
                s.getAccountName(), s.getAccountNumber(), s.getBankAddress(), s.getSwiftCode(), s.getCurrency(),
                s.getPurpose(), s.getNotes(), s.isFavorite(), s.getStatus(), s.getCreatedAt(), s.getUpdatedAt(),
                s.getQrCodeStorageKey() != null, s.getQrCodeFileName(), s.isReadyForPayment());
    }

    private static SupplierSummaryResponse toSummary(Supplier s) {
        return new SupplierSummaryResponse(
                s.getId(), s.getType(), s.getDisplayName(), s.getCountry(), s.getCity(),
                mask(s.getAccountNumber()), s.getPurpose(), s.isFavorite(), s.getStatus(), s.getCreatedAt(),
                s.isReadyForPayment());
    }

    /** Ne conserve que les 4 derniers caracteres, ex. {@code ******1234} — voir section 18. */
    private static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "******";
        }
        String visible = accountNumber.substring(accountNumber.length() - 4);
        return "******" + visible;
    }
}
