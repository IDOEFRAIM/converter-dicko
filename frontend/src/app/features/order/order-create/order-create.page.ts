import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { OrderService } from '../../../core/services/order.service';
import { QuoteService } from '../../../core/services/quote.service';
import { SupplierService } from '../../../core/services/supplier.service';
import { errorCode, extractErrorMessage } from '../../../core/services/api-error.util';
import { IdempotencyAttempt } from '../../../core/services/idempotency.util';
import {
  BeneficiaryType,
  BENEFICIARY_IDENTIFIER_LABELS,
  CreateOrderRequest,
} from '../../../core/models/order.model';
import { Purpose, PURPOSE_OPTIONS } from '../../../core/models/common.model';
import { SupplierSummary } from '../../../core/models/supplier.model';
import { openConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

/**
 * Seuil (XOF) a partir duquel un transfert doit etre confirme sur WhatsApp
 * (retour client sept. 2026 : "a partir de plus de 02 millions tu dois etre
 * ramene sur WhatsApp pour confirmer ton ordre") -- une simple orientation
 * vers un canal humain pour les gros montants, jamais une regle metier
 * serveur : l'ordre est deja cree normalement (miroir exact du comportement
 * mobile, voir AppConfig.whatsAppConfirmationThresholdXof). Numero corrige
 * (retour client sept. 2026) : l'ancien, 71 00 25 25, etait errone.
 */
const WHATSAPP_CONFIRMATION_THRESHOLD_XOF = 2_000_000;
const WHATSAPP_CONFIRMATION_PHONE_DISPLAY = '65 38 23 37';
const WHATSAPP_CONFIRMATION_PHONE_E164 = '22665382337';

type BeneficiarySource = 'SUPPLIER' | 'MANUAL';

/**
 * Choix du beneficiaire pour un ordre : un fournisseur deja enregistre (le backend copie
 * ses coordonnees dans un snapshot immuable), ou une saisie ponctuelle. Une cle d'idempotence
 * est generee pour la tentative et conservee tant qu'elle n'a pas abouti.
 */
@Component({
  selector: 'app-order-create-page',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-create.page.html',
})
export class OrderCreatePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orderService = inject(OrderService);
  private readonly quoteService = inject(QuoteService);
  private readonly supplierService = inject(SupplierService);
  private readonly dialog = inject(MatDialog);

  readonly quoteId = signal<string | null>(null);
  /** Non nul quand cet ordre contribue a une Ruee collective (mission "differenciation
   * marketing", Lot 3) — voir quote-create.page.ts / quote-detail.page.ts. */
  readonly poolId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly suppliers = signal<SupplierSummary[]>([]);
  /** null tant que non vérifié ; false = liquidité CNY actuellement insuffisante pour ce devis. */
  readonly liquiditySufficient = signal<boolean | null>(null);
  readonly quoteAmountXof = signal<string | null>(null);

  readonly purposeOptions = PURPOSE_OPTIONS;

  readonly hasSuppliers = computed(() => this.suppliers().length > 0);
  readonly needsWhatsAppConfirmation = computed(
    () => Number(this.quoteAmountXof() ?? 0) >= WHATSAPP_CONFIRMATION_THRESHOLD_XOF,
  );
  readonly whatsAppPhoneDisplay = WHATSAPP_CONFIRMATION_PHONE_DISPLAY;

  private readonly idempotency = new IdempotencyAttempt();

  readonly form = new FormGroup({
    source: new FormControl<BeneficiarySource>('MANUAL', { nonNullable: true }),
    supplierId: new FormControl<string>(''),
    type: new FormControl<BeneficiaryType>('CHINESE_BANK_ACCOUNT', { nonNullable: true }),
    fullName: new FormControl('', { nonNullable: true }),
    identifier: new FormControl('', { nonNullable: true }),
    bankName: new FormControl(''),
    bankBranch: new FormControl(''),
    purpose: new FormControl<string>(''),
    purposeDetails: new FormControl(''),
    note: new FormControl(''),
  });

  get useSupplier(): boolean {
    return this.form.controls.source.value === 'SUPPLIER';
  }

  get isBankAccount(): boolean {
    return this.form.controls.type.value === 'CHINESE_BANK_ACCOUNT';
  }

  get identifierLabel(): string {
    return BENEFICIARY_IDENTIFIER_LABELS[this.form.controls.type.value as BeneficiaryType];
  }

  ngOnInit(): void {
    const quoteId = this.route.snapshot.queryParamMap.get('quoteId');
    if (!quoteId) {
      this.errorMessage.set('Aucun devis a associer. Recommencez depuis un nouveau devis.');
      return;
    }
    this.quoteId.set(quoteId);
    this.poolId.set(this.route.snapshot.queryParamMap.get('poolId'));

    // Vérifie la faisabilité AVANT que l'utilisateur ne saisisse le bénéficiaire :
    // le devis est-il utilisable et la liquidité CNY couvre-t-elle son montant ?
    this.orderService.checkFeasibility(quoteId).subscribe({
      next: (response) => this.liquiditySufficient.set(response.data.sufficientLiquidity),
      error: (error) => this.errorMessage.set(extractErrorMessage(error)),
    });

    // Uniquement pour l'avertissement WhatsApp au-dela du seuil (needsWhatsAppConfirmation) --
    // un echec ici ne bloque jamais la suite du parcours, le montant reste affiche par
    // l'ecran de devis precedent.
    this.quoteService.get(quoteId).subscribe({
      next: (response) => this.quoteAmountXof.set(response.data.amountXof),
      error: () => undefined,
    });

    // Retour beta-testeur sept. 2026 : "la facon de lui proposer un fournisseur lors de la
    // conversion, c'est tres mal gere" -- present uniquement quand ce formulaire a ete rouvert
    // depuis "+ Enregistrer un nouveau fournisseur"/"Ajouter un fournisseur Alipay/WeChat" et que
    // sa creation a reussi (voir supplier-form.page.ts, navigateAfterSave). Permet de
    // pre-selectionner directement ce fournisseur plutot que de forcer l'utilisateur a le
    // re-choisir dans une liste qu'il vient tout juste de faire grandir lui-meme.
    const newSupplierId = this.route.snapshot.queryParamMap.get('newSupplierId');

    this.supplierService.list(0, 100, 'ACTIVE').subscribe({
      next: (response) => {
        this.suppliers.set(response.data.content);
        // Si le client a deja des fournisseurs, on le lui propose par defaut.
        if (response.data.content.length > 0) {
          this.form.controls.source.setValue('SUPPLIER');
        }
        if (newSupplierId && response.data.content.some((s) => s.id === newSupplierId)) {
          this.form.controls.supplierId.setValue(newSupplierId);
        }
      },
      error: () => undefined,
    });
  }

  submit(): void {
    const quoteId = this.quoteId();
    if (!quoteId || this.loading()) {
      return;
    }

    if (!this.validate()) {
      return;
    }

    const v = this.form.getRawValue();
    const purpose = (v.purpose || null) as Purpose | null;
    const purposeDetails = v.purposeDetails?.trim() || null;
    const note = v.note?.trim() || null;

    const poolId = this.poolId();
    const request: CreateOrderRequest = this.useSupplier
      ? { quoteId, beneficiary: null, supplierId: v.supplierId || null, purpose, purposeDetails, note, poolId }
      : {
          quoteId,
          beneficiary: {
            type: v.type,
            fullName: v.fullName.trim(),
            identifier: v.identifier.trim(),
            bankName: v.bankName?.trim() || null,
            bankBranch: v.bankBranch?.trim() || null,
          },
          purpose,
          purposeDetails,
          note,
          poolId,
        };

    // Meme requete rejouee apres un echec => meme cle ; requete modifiee => nouvelle cle.
    const idempotencyKey = this.idempotency.keyFor(request);

    this.loading.set(true);
    this.errorMessage.set(null);
    this.orderService.create(request, idempotencyKey).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.idempotency.complete();
        const order = response.data;
        if (Number(order.amountXof) >= WHATSAPP_CONFIRMATION_THRESHOLD_XOF) {
          this.showWhatsAppConfirmation(order.amountXof, order.reference);
        }
        // Retour beta-testeur sept. 2026 : "il est oblige de tourner et reflechir" -- un ordre
        // vient toujours de naitre AWAITING_PAYMENT (voir OrderService.create backend), l'etape
        // suivante est TOUJOURS payer. On y va donc directement plutot que de faire atterrir sur
        // le detail de l'ordre, ou l'utilisateur devait chercher lui-meme comment payer. Une
        // contribution a une Ruee collective reste une exception deliberee (mission
        // "differenciation marketing", Lot 3) : son detail (thermometre, celebration eventuelle)
        // prime, l'ordre restant accessible depuis l'historique comme d'habitude.
        const poolId = this.poolId();
        this.router.navigate(poolId ? ['/pools', poolId] : ['/orders', order.id, 'payment']);
      },
      error: (error) => {
        this.loading.set(false);
        // Le blocage tresorerie reste reel (invariant comptable, voir TreasuryService.reserve
        // backend) -- seul le TON change, jamais le message brut backend qui expose des
        // montants internes ("disponible X, demande Y") -- retour beta-testeur sept. 2026.
        this.errorMessage.set(
          errorCode(error) === 'INSUFFICIENT_TREASURY'
            ? 'Pour des raisons de maintenance, ce transfert va prendre un peu plus de temps que prevu. Reessayez un peu plus tard.'
            : extractErrorMessage(error),
        );
      },
    });
  }

  /** Retour client sept. 2026 : voir WHATSAPP_CONFIRMATION_THRESHOLD_XOF ci-dessus. L'ordre est
   * deja cree normalement ; cette boite oriente simplement vers le canal humain attendu pour les
   * gros montants, sans bloquer la navigation vers le detail de l'ordre. */
  private showWhatsAppConfirmation(amountXof: string, reference: string): void {
    const amountLabel = new MoneyPipe().transform(amountXof, 'XOF');
    openConfirmDialog(this.dialog, {
      title: 'Confirmation par WhatsApp',
      message:
        `Votre transfert de ${amountLabel} doit etre confirme par WhatsApp au ` +
        `${WHATSAPP_CONFIRMATION_PHONE_DISPLAY} avant traitement. Indiquez la reference ${reference}.`,
      confirmLabel: 'Ouvrir WhatsApp',
      cancelLabel: 'Plus tard',
    }).subscribe((result) => {
      if (!result) {
        return;
      }
      const message = `Bonjour, je confirme mon transfert de ${amountLabel} (reference ${reference}).`;
      window.open(
        `https://wa.me/${WHATSAPP_CONFIRMATION_PHONE_E164}?text=${encodeURIComponent(message)}`,
        '_blank',
      );
    });
  }

  private validate(): boolean {
    const c = this.form.controls;
    if (this.useSupplier) {
      if (!c.supplierId.value) {
        c.supplierId.setErrors({ required: true });
        c.supplierId.markAsTouched();
        return false;
      }
      return true;
    }

    let ok = true;
    if (!c.fullName.value.trim()) {
      c.fullName.setErrors({ required: true });
      c.fullName.markAsTouched();
      ok = false;
    }
    if (!c.identifier.value.trim()) {
      c.identifier.setErrors({ required: true });
      c.identifier.markAsTouched();
      ok = false;
    }
    if (this.isBankAccount && !c.bankName.value) {
      c.bankName.setErrors({ required: true });
      c.bankName.markAsTouched();
      ok = false;
    }
    return ok;
  }
}
