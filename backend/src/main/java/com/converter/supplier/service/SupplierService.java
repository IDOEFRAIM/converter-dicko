package com.converter.supplier.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.security.OwnershipService;
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

    private final SupplierRepository supplierRepository;
    private final OwnershipService ownershipService;
    private final AuditService auditService;

    public SupplierService(SupplierRepository supplierRepository,
                           OwnershipService ownershipService,
                           AuditService auditService) {
        this.supplierRepository = supplierRepository;
        this.ownershipService = ownershipService;
        this.auditService = auditService;
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
                s.getPurpose(), s.getNotes(), s.isFavorite(), s.getStatus(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private static SupplierSummaryResponse toSummary(Supplier s) {
        return new SupplierSummaryResponse(
                s.getId(), s.getType(), s.getDisplayName(), s.getCountry(), s.getCity(),
                mask(s.getAccountNumber()), s.getPurpose(), s.isFavorite(), s.getStatus(), s.getCreatedAt());
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
