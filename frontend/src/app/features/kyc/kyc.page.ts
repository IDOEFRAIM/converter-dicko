import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  KYC_DOCUMENT_TYPE_LABELS,
  KycDocumentType,
  KycSubmission,
  kycNeedsBack,
} from '../../core/models/kyc.model';
import { extractErrorMessage } from '../../core/services/api-error.util';
import { AuthService } from '../../core/services/auth.service';
import { KycService } from '../../core/services/kyc.service';

type Slot = 'front' | 'back' | 'selfie';

/**
 * Verification d'identite — copie de `KycPage` (mobile) : statut du dernier dossier
 * (verifie / en examen / a corriger), puis choix de la piece et trois photos. Sur le
 * web, `<input type="file" accept="image/*">` propose nativement appareil photo OU galerie.
 */
@Component({
  selector: 'app-kyc-page',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './kyc.page.html',
  styleUrl: './kyc.page.scss',
})
export class KycPage implements OnInit {
  private readonly kycService = inject(KycService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly documentTypes = Object.keys(KYC_DOCUMENT_TYPE_LABELS) as KycDocumentType[];
  readonly labels = KYC_DOCUMENT_TYPE_LABELS;

  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly submission = signal<KycSubmission | null>(null);
  readonly documentType = signal<KycDocumentType>('NATIONAL_ID');
  readonly files = signal<Record<Slot, File | null>>({ front: null, back: null, selfie: null });
  readonly previews = signal<Record<Slot, string | null>>({
    front: null,
    back: null,
    selfie: null,
  });

  readonly status = computed(() => this.submission()?.status ?? 'NONE');
  readonly showForm = computed(() => this.status() === 'NONE' || this.status() === 'REJECTED');
  readonly needsBack = computed(() => kycNeedsBack(this.documentType()));
  readonly captureSlots = computed<{ slot: Slot; label: string; hint: string }[]>(() => [
    { slot: 'front', label: 'Recto de la piece', hint: 'Lisible, sans reflet' },
    ...(this.needsBack()
      ? [{ slot: 'back' as Slot, label: 'Verso de la piece', hint: 'Lisible, sans reflet' }]
      : []),
    { slot: 'selfie', label: 'Selfie', hint: 'Visage bien visible' },
  ]);
  readonly canSubmit = computed(() => {
    const f = this.files();
    return !this.submitting() && !!f.front && !!f.selfie && (!this.needsBack() || !!f.back);
  });

  constructor() {
    this.destroyRef.onDestroy(() =>
      Object.values(this.previews()).forEach((url) => url && URL.revokeObjectURL(url)),
    );
  }

  ngOnInit(): void {
    this.kycService.mySubmission().subscribe({
      next: (r) => {
        this.submission.set(r.data);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  hasFile(slot: Slot): boolean {
    return !!this.files()[slot];
  }

  preview(slot: Slot): string | null {
    return this.previews()[slot];
  }

  onFile(slot: Slot, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) {
      return;
    }
    const previous = this.previews()[slot];
    if (previous) {
      URL.revokeObjectURL(previous);
    }
    this.files.update((f) => ({ ...f, [slot]: file }));
    this.previews.update((p) => ({ ...p, [slot]: URL.createObjectURL(file) }));
  }

  submit(): void {
    const f = this.files();
    if (!this.canSubmit() || !f.front || !f.selfie) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.kycService
      .submit(this.documentType(), f.front, this.needsBack() ? f.back : null, f.selfie)
      .subscribe({
        next: (r) => {
          this.submission.set(r.data);
          this.submitting.set(false);
          this.snackBar.open('Dossier envoye.', undefined, { duration: 3000 });
          this.auth.restoreSession().subscribe({ error: () => undefined });
        },
        error: (error) => {
          const message = extractErrorMessage(error);
          this.errorMessage.set(message);
          this.submitting.set(false);
          this.snackBar.open(message, undefined, { duration: 4000 });
        },
      });
  }
}
