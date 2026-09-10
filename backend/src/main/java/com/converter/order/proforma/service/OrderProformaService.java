package com.converter.order.proforma.service;

import com.converter.business.profile.domain.BusinessProfile;
import com.converter.business.profile.repository.BusinessProfileRepository;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.Beneficiary;
import com.converter.order.domain.Order;
import com.converter.order.proforma.model.ProformaDocument;
import com.converter.order.proforma.model.ProformaInvoiceModel;
import com.converter.order.proforma.pdf.ProformaPdfGenerator;
import com.converter.order.receipt.model.ReceiptBeneficiary;
import com.converter.order.repository.BeneficiaryRepository;
import com.converter.order.repository.OrderRepository;
import com.converter.rate.provider.RateProvider;
import com.converter.security.OwnershipService;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Construit et rend la <b>facture proforma</b> d'un paiement fournisseur (remarque produit #3).
 *
 * <p>Meme discipline documentaire que {@code OrderReceiptService} : {@code @Transactional
 * readOnly = true}, aucune mutation, aucun recalcul de pricing — toutes les valeurs financieres
 * proviennent des colonnes deja figees d'{@code Order}, le beneficiaire du snapshot
 * {@code Beneficiary} de l'ordre (jamais du {@code Supplier} actuel). Le seul enrichissement est
 * l'identite legale de l'acheteur, lue sur son {@code BusinessProfile} s'il en a un.
 *
 * <p>Disponible <b>quel que soit le statut</b> de l'ordre (une proforma s'emet avant paiement),
 * mais <b>uniquement pour un ordre vers un fournisseur enregistre</b> ({@code supplierId} non nul) :
 * c'est le signal du parcours "payer un fournisseur" (par opposition a un simple change personnel).
 */
@Service
public class OrderProformaService {

    private final OrderRepository orderRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final SettingsService settingsService;
    private final OwnershipService ownershipService;
    private final ProformaPdfGenerator pdfGenerator;

    public OrderProformaService(OrderRepository orderRepository,
                                BeneficiaryRepository beneficiaryRepository,
                                UserRepository userRepository,
                                BusinessProfileRepository businessProfileRepository,
                                SettingsService settingsService,
                                OwnershipService ownershipService,
                                ProformaPdfGenerator pdfGenerator) {
        this.orderRepository = orderRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.userRepository = userRepository;
        this.businessProfileRepository = businessProfileRepository;
        this.settingsService = settingsService;
        this.ownershipService = ownershipService;
        this.pdfGenerator = pdfGenerator;
    }

    @Transactional(readOnly = true)
    public ProformaDocument generate(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> notFound(orderId));
        ownershipService.assertOwnedBy(order.getUserId(), userId, ErrorCode.ORDER_NOT_FOUND,
                "Ordre introuvable : " + orderId);

        // Paiement fournisseur = fournisseur enregistre OU montant au-dessus du seuil (#3).
        BigDecimal threshold = settingsService.getDecimal(SettingKey.SUPPLIER_PAYMENT_THRESHOLD_XOF);
        boolean isSupplierPayment = order.getSupplierId() != null
                || order.getAmountXof().compareTo(threshold) >= 0;
        if (!isSupplierPayment) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La facture proforma n'est disponible que pour un paiement fournisseur "
                            + "(fournisseur enregistre ou montant a partir de " + threshold.toPlainString() + " XOF).");
        }

        User customer = userRepository.findById(order.getUserId()).orElseThrow(() -> notFound(orderId));
        Beneficiary beneficiary = beneficiaryRepository.findByOrderId(orderId).orElseThrow(() -> notFound(orderId));
        BusinessProfile business = businessProfileRepository.findByUserId(order.getUserId()).orElse(null);

        ProformaInvoiceModel model = new ProformaInvoiceModel(
                "PRO-" + order.getReference(),
                order.getCreatedAt(),
                order.getReference(),
                order.getStatus(),
                customer.fullName(),
                business == null ? null : business.getBusinessName(),
                business == null ? null : business.getRegistrationNumber(),
                business == null ? null : composeAddress(business),
                order.getAmountXof(), order.getFeeXof(), order.getNetAmountXof(),
                order.getCustomerRate(), order.getAmountCny(),
                RateProvider.DEFAULT_CURRENCY_PAIR,
                new ReceiptBeneficiary(beneficiary.getFullName(), beneficiary.getType(),
                        mask(beneficiary.getIdentifier()), beneficiary.getBankName(), beneficiary.getBankBranch()),
                order.getPurpose(), order.getPurposeDetails());

        return new ProformaDocument(pdfGenerator.generate(model), "proforma-" + order.getReference() + ".pdf");
    }

    private static String composeAddress(BusinessProfile business) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, business.getAddress());
        appendPart(builder, business.getCity());
        appendPart(builder, business.getCountry());
        return builder.isEmpty() ? null : builder.toString();
    }

    private static void appendPart(StringBuilder builder, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(", ");
        }
        builder.append(part.trim());
    }

    /** Meme convention de masquage que {@code OrderReceiptService} : seuls les 4 derniers visibles. */
    static String mask(String identifier) {
        if (identifier == null || identifier.length() <= 4) {
            return "******";
        }
        return "******" + identifier.substring(identifier.length() - 4);
    }

    private BusinessException notFound(UUID orderId) {
        return new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Ordre introuvable : " + orderId);
    }
}
