import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PoolService } from '../../../core/services/pool.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';

const DURATION_OPTIONS = [15, 30, 60, 120];

/**
 * Creation d'une Ruee collective (mission "differenciation marketing", Lot 3) : un objectif de
 * volume et une echeance, rien d'autre — le createur devient automatiquement le premier participant.
 */
@Component({
  selector: 'app-pool-create-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pool-create.page.html',
  styleUrl: './pool-create.page.scss',
})
export class PoolCreatePage {
  private readonly poolService = inject(PoolService);
  private readonly router = inject(Router);

  readonly durationOptions = DURATION_OPTIONS;
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = new FormGroup({
    targetAmountXof: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1000)],
    }),
    durationMinutes: new FormControl<number>(30, { nonNullable: true }),
  });

  durationLabel(minutes: number): string {
    return minutes < 60 ? `${minutes} min` : `${minutes / 60} h`;
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const value = this.form.getRawValue();

    this.poolService
      .create({ targetAmountXof: String(value.targetAmountXof), durationMinutes: value.durationMinutes })
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.router.navigate(['/pools', response.data.id]);
        },
        error: (error) => {
          this.submitting.set(false);
          this.errorMessage.set(extractErrorMessage(error));
        },
      });
  }
}
