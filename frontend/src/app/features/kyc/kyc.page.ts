import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { KycService } from '../../core/services/kyc.service';
import { extractErrorMessage } from '../../core/services/api-error.util';
import {
  KYC_DOCUMENT_TYPE_LABELS,
  KycDocumentType,
  KycSubmission,
  kycDocumentNeedsBack,
} from '../../core/models/kyc.model';

type KycSlot = 'front' | 'back' | 'selfie';

/**
 * Verification d'identite en libre-service (remarque produit #6) — parite web du parcours
 * mobile (`KycPage`) : memes trois photos (recto/verso/selfie), memes trois vues pilotees par le
 * statut du dernier dossier (PENDING / APPROVED / REJECTED / aucun dossier -> formulaire).
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

  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly submission = signal<KycSubmission | null>(null);

  readonly documentType = signal<KycDocumentType>('NATIONAL_ID');
  readonly documentTypes: KycDocumentType[] = ['NATIONAL_ID', 'PASSPORT', 'RESIDENCE_PERMIT'];
  readonly documentLabels = KYC_DOCUMENT_TYPE_LABELS;

  readonly frontFile = signal<File | null>(null);
  readonly backFile = signal<File | null>(null);
  readonly selfieFile = signal<File | null>(null);
  readonly frontPreview = signal<string | null>(null);
  readonly backPreview = signal<string | null>(null);
  readonly selfiePreview = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.kycService.mySubmission().subscribe({
      next: (response) => {
        this.submission.set(response.data);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  needsBack(): boolean {
    return kycDocumentNeedsBack(this.documentType());
  }

  showForm(): boolean {
    const status = this.submission()?.status;
    return status === undefined || status === 'REJECTED';
  }

  selectDocumentType(type: KycDocumentType): void {
    if (this.documentType() === type) return;
    this.documentType.set(type);
    if (!kycDocumentNeedsBack(type)) {
      this.setFile('back', null);
    }
  }

  onFileSelected(slot: KycSlot, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.setFile(slot, file);
    // Autorise de reprendre la MEME photo (meme nom/contenu) : sans ce reset, le navigateur ne
    // redeclenche pas l'evenement 'change' pour une selection identique a la precedente.
    input.value = '';
  }

  canSubmit(): boolean {
    return (
      !this.submitting() &&
      this.frontFile() !== null &&
      this.selfieFile() !== null &&
      (!this.needsBack() || this.backFile() !== null)
    );
  }

  submit(): void {
    if (!this.canSubmit()) return;
    const front = this.frontFile();
    const selfie = this.selfieFile();
    if (!front || !selfie) return;

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.kycService.submit(this.documentType(), front, this.needsBack() ? this.backFile() : null, selfie).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.submission.set(response.data);
        this.setFile('front', null);
        this.setFile('back', null);
        this.setFile('selfie', null);
      },
      error: (error) => {
        this.submitting.set(false);
        this.errorMessage.set(extractErrorMessage(error));
      },
    });
  }

  private setFile(slot: KycSlot, file: File | null): void {
    const fileSignal = slot === 'front' ? this.frontFile : slot === 'back' ? this.backFile : this.selfieFile;
    const previewSignal =
      slot === 'front' ? this.frontPreview : slot === 'back' ? this.backPreview : this.selfiePreview;
    const previousUrl = previewSignal();
    if (previousUrl) {
      URL.revokeObjectURL(previousUrl);
    }
    fileSignal.set(file);
    previewSignal.set(file ? URL.createObjectURL(file) : null);
  }
}
