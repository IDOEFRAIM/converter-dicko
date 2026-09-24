import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { PoolService } from '../../../core/services/pool.service';

/** "Lancer une Ruee" — copie de `PoolCreatePage` (mobile). */
@Component({
  selector: 'app-pool-create-page',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="screen" (ngSubmit)="submit()">
      <p class="t-caption">
        Objectif atteint a temps = reduction pour chaque participant sur son prochain transfert.
      </p>

      <p class="t-eyebrow pc-label">Objectif de volume</p>
      <mat-form-field appearance="outline" class="form__field">
        <input
          matInput
          type="number"
          inputmode="decimal"
          min="1"
          name="target"
          [(ngModel)]="target"
          placeholder="0"
          class="t-metric-medium"
        />
        <span matTextSuffix>XOF</span>
      </mat-form-field>

      <p class="t-eyebrow pc-label">Duree</p>
      <div class="chips">
        @for (m of durations; track m) {
          <button
            type="button"
            class="chip"
            [class.chip--selected]="duration() === m"
            (click)="duration.set(m)"
          >
            {{ m < 60 ? m + ' min' : m / 60 + ' h' }}
          </button>
        }
      </div>

      @if (error()) {
        <p class="t-body pc-error">{{ error() }}</p>
      }

      <button mat-flat-button type="submit" class="pc-submit" [disabled]="submitting()">
        @if (submitting()) {
          <mat-spinner diameter="20" />
        } @else {
          Lancer la Ruee
        }
      </button>
    </form>
  `,
  styles: [
    `
      .pc-label {
        margin: 24px 0 8px;
      }
      .pc-error {
        color: var(--c-negative);
        margin-top: 12px;
      }
      .pc-submit {
        width: 100%;
        margin-top: 32px;
      }
    `,
  ],
})
export class PoolCreatePage {
  private readonly poolService = inject(PoolService);
  private readonly router = inject(Router);

  readonly durations = [15, 30, 60, 120];
  readonly duration = signal(30);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  target: number | null = null;

  submit(): void {
    if (this.submitting()) {
      return;
    }
    if (!this.target || this.target <= 0) {
      this.error.set('Saisissez un montant valide.');
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.poolService
      .create({ targetAmountXof: String(this.target), durationMinutes: this.duration() })
      .subscribe({
        next: (r) => this.router.navigate(['/pay/pools', r.data.id], { replaceUrl: true }),
        error: (e) => {
          this.error.set(extractErrorMessage(e));
          this.submitting.set(false);
        },
      });
  }
}
