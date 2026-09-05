package com.converter.order.receipt.service;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.Beneficiary;
import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.order.receipt.model.ReceiptBeneficiary;
import com.converter.order.receipt.model.ReceiptDocument;
import com.converter.order.receipt.model.ReceiptRefund;
import com.converter.order.receipt.model.TransferReceiptModel;
import com.converter.order.receipt.pdf.ReceiptPdfGenerator;
import com.converter.order.repository.BeneficiaryRepository;
import com.converter.order.repository.OrderRepository;
import com.converter.payment.domain.Payment;
import com.converter.payment.repository.PaymentRepository;
import com.converter.rate.provider.RateProvider;
import com.converter.refund.domain.Refund;
import com.converter.refund.repository.RefundRepository;
import com.converter.security.OwnershipService;
import com.converter.settlement.domain.Settlement;
import com.converter.settlement.repository.SettlementRepository;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Construit et rend le justificatif de transaction d'un {@code Order}.
 *
 * <p><b>Strictement documentaire</b> : aucune mutation (voir la signature {@code @Transactional
 * readOnly = true} de {@link #generate}), aucun recalcul de pricing. Ce service n'a et ne doit
 * jamais avoir de dependance vers {@code RateEngine}/{@code SettingsService}/tout ce qui
 * calculerait un taux "courant" — garantie structurelle identique a celle deja etablie pour
 * {@code PublicRateSnapshotService} (Phase 5) et {@code RateAlertService} (Phase 6) : toutes les
 * valeurs financieres du modele proviennent exclusivement des colonnes deja figees d'{@code Order}
 * (copie du {@code Quote} au moment de sa creation, jamais relue ni recalculee).
 *
 * <p>Le beneficiaire vient exclusivement du snapshot {@link Beneficiary} de l'ordre, jamais du
 * {@code Supplier} actuel — une modification ulterieure du fournisseur n'a donc structurellement
 * aucun effet sur un justificatif deja emis (meme invariant que la Phase 2).
 *
 * <p>Meme patron de chargement cible que {@code OrderTrackingService} : au plus quelques lectures
 * par identifiant exact (ordre, beneficiaire, utilisateur, paiement, reglement, remboursement),
 * jamais de {@code findAll}.
 */
@Service
public class OrderReceiptService {

    private final OrderRepository orderRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;
    private final RefundRepository refundRepository;
    private final OwnershipService ownershipService;
    private final ReceiptPdfGenerator pdfGenerator;

    public OrderReceiptService(OrderRepository orderRepository,
                               BeneficiaryRepository beneficiaryRepository,
                               UserRepository userRepository,
                               PaymentRepository paymentRepository,
                               SettlementRepository settlementRepository,
                               RefundRepository refundRepository,
                               OwnershipService ownershipService,
                               ReceiptPdfGenerator pdfGenerator) {
        this.orderRepository = orderRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.settlementRepository = settlementRepository;
        this.refundRepository = refundRepository;
        this.ownershipService = ownershipService;
        this.pdfGenerator = pdfGenerator;
    }

    /**
     * Genere le PDF a la demande (section 20, option A) : aucune persistance, aucune cle de
     * stockage a retenir — le meme ordre produit toujours le meme contenu financier/beneficiaire
     * (section 30), sans qu'une migration ou un module de stockage supplementaire soit necessaire.
     *
     * <p>Disponible uniquement pour un ordre {@code COMPLETED} (section 10) : aucun justificatif
     * provisoire n'est invente pour un ordre encore en cours — {@code 409 INVALID_ORDER_STATE}
     * sinon, code d'erreur deja existant, reutilise tel quel.
     */
    @Transactional(readOnly = true)
    public ReceiptDocument generate(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> notFound(orderId));
        ownershipService.assertOwnedBy(order.getUserId(), userId, ErrorCode.ORDER_NOT_FOUND,
                "Ordre introuvable : " + orderId);

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE,
                    "Le justificatif n'est disponible que pour un ordre termine (statut actuel : "
                            + order.getStatus() + ").");
        }

        User customer = userRepository.findById(order.getUserId()).orElseThrow(() -> notFound(orderId));
        Beneficiary beneficiary = beneficiaryRepository.findByOrderId(orderId).orElseThrow(() -> notFound(orderId));
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        Settlement settlement = settlementRepository.findByOrderId(orderId).orElse(null);
        Refund refund = payment == null ? null
                : refundRepository.findFirstByPaymentIdOrderByCreatedAtDesc(payment.getId()).orElse(null);

        TransferReceiptModel model = buildModel(order, customer, beneficiary, payment, settlement, refund);
        byte[] pdf = pdfGenerator.generate(model);
        return new ReceiptDocument(pdf, fileNameFor(order));
    }

    private TransferReceiptModel buildModel(Order order, User customer, Beneficiary beneficiary, Payment payment,
                                            Settlement settlement, Refund refund) {
        return new TransferReceiptModel(
                order.getId(),
                order.getReference(),
                order.getCreatedAt(),
                order.getCompletedAt(),
                order.getStatus(),
                customer.fullName(),
                order.getAmountXof(),
                order.getFeeXof(),
                order.getNetAmountXof(),
                order.getCustomerRate(),
                order.getAmountCny(),
                RateProvider.DEFAULT_CURRENCY_PAIR,
                toReceiptBeneficiary(beneficiary),
                order.getPurpose(),
                order.getPurposeDetails(),
                payment == null ? null : payment.getTransactionReference(),
                payment == null ? null : payment.getConfirmedAt(),
                settlement == null ? null : settlement.getSettlementReference(),
                settlement == null ? null : settlement.getExecutedAt(),
                refund == null ? null : toReceiptRefund(refund));
    }

    private static ReceiptBeneficiary toReceiptBeneficiary(Beneficiary beneficiary) {
        return new ReceiptBeneficiary(beneficiary.getFullName(), beneficiary.getType(),
                mask(beneficiary.getIdentifier()), beneficiary.getBankName(), beneficiary.getBankBranch());
    }

    private static ReceiptRefund toReceiptRefund(Refund refund) {
        // "Date" du remboursement : la resolution (processedAt) prime des qu'elle existe
        // (PROCESSED/REJECTED) -- un remboursement encore PENDING n'a que sa date de creation.
        var date = refund.getProcessedAt() != null ? refund.getProcessedAt() : refund.getCreatedAt();
        return new ReceiptRefund(refund.getStatus(), refund.getAmountXof(), date, refund.getTransactionReference());
    }

    /**
     * Ne conserve que les 4 derniers caracteres, ex. {@code ******1234} — meme convention que
     * {@code SupplierService#mask} (section 8) : un document PDF telechargeable/imprimable/
     * partageable justifie une minimisation au moins aussi stricte qu'une reponse de liste API.
     */
    static String mask(String identifier) {
        if (identifier == null || identifier.length() <= 4) {
            return "******";
        }
        return "******" + identifier.substring(identifier.length() - 4);
    }

    private static String fileNameFor(Order order) {
        return "transaction-" + order.getReference() + ".pdf";
    }

    private BusinessException notFound(UUID orderId) {
        return new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Ordre introuvable : " + orderId);
    }
}
