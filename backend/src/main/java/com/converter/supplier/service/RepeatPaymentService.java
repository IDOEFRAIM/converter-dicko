package com.converter.supplier.service;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.dto.CreateOrderRequest;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.order.service.OrderService;
import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.quote.service.QuoteService;
import com.converter.supplier.domain.Purpose;
import com.converter.supplier.domain.Supplier;
import com.converter.supplier.domain.SupplierStatus;
import com.converter.supplier.dto.PayAgainRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestre "payer a nouveau" : {@code Supplier} enregistre -&gt; nouveau {@code Quote} (pricing
 * courant) -&gt; nouvel {@code Order}. N'implemente <b>aucune</b> logique financiere propre —
 * chaque etape delegue integralement a {@link QuoteService}/{@link OrderService}, exactement
 * comme le ferait un client qui creerait un devis puis un ordre via les endpoints existants.
 *
 * <p><b>Ce que ce service ne fait jamais</b> : copier un ancien {@code Order}/{@code Quote},
 * reutiliser un {@code customerRate}/des frais anterieurs, creer directement un
 * {@code Payment}/{@code Settlement}, ou toucher la tresorerie — tout cela reste exclusivement
 * du ressort du flux normal, inchange, declenche par le client apres coup (paiement, revue
 * admin, reglement).
 *
 * <p><b>Frontiere transactionnelle</b> : {@code @Transactional} (propagation REQUIRED, par
 * defaut) englobe les trois appels — {@code QuoteService.create}/{@code accept} et
 * {@code OrderService.create} restent chacun {@code @Transactional} independamment (inchange),
 * mais en les invoquant depuis une methode elle-meme transactionnelle, ils rejoignent une seule
 * et meme transaction physique : si {@code OrderService.create} echoue (fournisseur desactive
 * entre-temps, plafond d'ordres ouverts atteint...), le {@code Quote} nouvellement cree est
 * annule avec le reste, jamais orphelin. C'est le meme motif deja utilise partout ailleurs dans
 * ce backend pour combiner plusieurs appels de service en une seule operation atomique (voir
 * {@code SettlementService.execute}, {@code PaymentService.confirm}) — aucune frontiere
 * transactionnelle <i>existante</i> n'est modifiee : les endpoints normaux (creer un devis,
 * l'accepter, creer un ordre separement) continuent chacun dans leur propre transaction HTTP,
 * exactement comme avant.
 */
@Service
public class RepeatPaymentService {

    private final SupplierService supplierService;
    private final QuoteService quoteService;
    private final OrderService orderService;

    public RepeatPaymentService(SupplierService supplierService, QuoteService quoteService, OrderService orderService) {
        this.supplierService = supplierService;
        this.quoteService = quoteService;
        this.orderService = orderService;
    }

    @Transactional
    public OrderDetailResponse payAgain(UUID supplierId, PayAgainRequest request, UUID userId) {
        // Ownership (404 si absent/d'un autre utilisateur) + statut, verifies avant toute
        // ecriture : un UUID de fournisseur ne doit jamais faire office d'autorisation en soi.
        Supplier supplier = supplierService.getEntityOwned(supplierId, userId);
        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SUPPLIER_INACTIVE,
                    "Ce fournisseur est desactive et ne peut plus etre utilise pour un nouveau paiement.");
        }

        // Le motif explicitement fourni a toujours priorite ; a defaut, le motif par defaut du
        // fournisseur (s'il en a un) est propose — jamais l'inverse (voir PayAgainRequest).
        Purpose purpose = request.purpose() != null ? request.purpose() : supplier.getPurpose();

        // Nouveau devis, taux/frais/marge du jour — jamais ceux d'une transaction passee.
        // Reutilise integralement QuoteService.create, aucun raccourci.
        QuoteResponse quote = quoteService.create(
                new CreateQuoteRequest(QuoteDirection.SEND_XOF, request.amountXof(), null), userId);
        quoteService.accept(quote.id(), userId);

        // beneficiary=null + supplierId : OrderService.create derive lui-meme le snapshot
        // Beneficiary depuis le fournisseur (logique deja construite en Phase 2, non dupliquee
        // ici). note=null : pay-again ne porte pas de champ note libre distinct de purposeDetails.
        return orderService.create(
                new CreateOrderRequest(quote.id(), null, null, supplierId, purpose, request.purposeDetails()),
                userId);
    }
}
