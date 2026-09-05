import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { BusinessService } from '../../core/services/business.service';
import { NotificationService } from '../../core/services/notification.service';
import { extractErrorMessage } from '../../core/services/api-error.util';
import {
  BusinessPaymentSummary,
  BusinessProfile,
  BusinessType,
  BUSINESS_TYPE_LABELS,
  BUSINESS_TYPE_OPTIONS,
} from '../../core/models/business.model';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { MoneyPipe } from '../../shared/pipes/money.pipe';

/**
 * Espace professionnel : profil (creation/mise a jour idempotente via `PUT`) et reporting
 * consolide. La distinction PERSONAL/BUSINESS vient uniquement de l'existence d'un profil
 * (un 404 => PERSONAL, on affiche alors le formulaire de creation). Le reporting affiche
 * exactement les valeurs backend — aucun recalcul.
 */
@Component({
  selector: 'app-business-page',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    PageHeaderComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './business.page.html',
  styleUrl: './business.page.scss',
})
export class BusinessPage implements OnInit {
  private readonly businessService = inject(BusinessService);
  private readonly notification = inject(NotificationService);

  readonly profile = signal<BusinessProfile | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly editing = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly summary = signal<BusinessPaymentSummary | null>(null);
  readonly summaryLoading = signal(false);
  readonly summaryError = signal<string | null>(null);

  readonly typeOptions = BUSINESS_TYPE_OPTIONS;
  readonly typeLabels = BUSINESS_TYPE_LABELS;

  readonly isBusiness = computed(() => this.profile() !== null);
  /** Formulaire visible : soit aucun profil (creation), soit edition explicite. */
  readonly showForm = computed(() => !this.isBusiness() || this.editing());

  readonly form = new FormGroup({
    businessName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    businessType: new FormControl<BusinessType>('IMPORTER', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    registrationNumber: new FormControl(''),
    country: new FormControl('Burkina Faso', { nonNullable: true, validators: [Validators.required] }),
    city: new FormControl(''),
    address: new FormControl(''),
  });

  ngOnInit(): void {
    this.businessService.getProfile().subscribe({
      next: (response) => {
        this.profile.set(response.data);
        this.patchForm(response.data);
        this.loading.set(false);
        this.loadSummary();
      },
      error: (error: unknown) => {
        this.loading.set(false);
        if (error instanceof HttpErrorResponse && error.status === 404) {
          // Pas de profil => utilisateur PERSONAL : on propose la creation, ce n'est pas une erreur.
          return;
        }
        this.errorMessage.set(extractErrorMessage(error));
      },
    });
  }

  private patchForm(profile: BusinessProfile): void {
    this.form.patchValue({
      businessName: profile.businessName,
      businessType: profile.businessType,
      registrationNumber: profile.registrationNumber ?? '',
      country: profile.country,
      city: profile.city ?? '',
      address: profile.address ?? '',
    });
  }

  startEdit(): void {
    const profile = this.profile();
    if (profile) {
      this.patchForm(profile);
    }
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.businessService
      .upsertProfile({
        businessName: v.businessName.trim(),
        businessType: v.businessType,
        registrationNumber: v.registrationNumber?.trim() || null,
        country: v.country.trim(),
        city: v.city?.trim() || null,
        address: v.address?.trim() || null,
      })
      .subscribe({
        next: (response) => {
          this.saving.set(false);
          this.editing.set(false);
          const isNew = this.profile() === null;
          this.profile.set(response.data);
          this.notification.success(isNew ? 'Profil professionnel cree.' : 'Profil mis a jour.');
          if (isNew) {
            this.loadSummary();
          }
        },
        error: (error) => {
          this.saving.set(false);
          this.notification.error(extractErrorMessage(error));
        },
      });
  }

  private loadSummary(): void {
    this.summaryLoading.set(true);
    this.summaryError.set(null);
    this.businessService.paymentsSummary().subscribe({
      next: (response) => {
        this.summary.set(response.data);
        this.summaryLoading.set(false);
      },
      error: (error) => {
        this.summaryError.set(extractErrorMessage(error));
        this.summaryLoading.set(false);
      },
    });
  }
}
