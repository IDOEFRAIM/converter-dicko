import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { PoolService } from '../../../core/services/pool.service';

/** "Rejoindre une Ruee" — resout le code puis ouvre le detail (ne rejoint jamais automatiquement). */
@Component({
  selector: 'app-pool-join-page',
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
    <form class="screen" (ngSubmit)="lookup()">
      <p class="t-caption">Entrez le code recu pour rejoindre une Ruee.</p>
      <mat-form-field appearance="outline" class="form__field pj-field">
        <mat-label>Code de la Ruee</mat-label>
        <input
          matInput
          name="code"
          autocapitalize="characters"
          autocomplete="off"
          placeholder="EX. AB12CD"
          class="t-metric-medium"
          [ngModel]="code"
          (ngModelChange)="code = $event.toUpperCase()"
        />
      </mat-form-field>
      @if (error()) {
        <p class="t-body pj-error">{{ error() }}</p>
      }
      <button mat-flat-button type="submit" class="pj-submit" [disabled]="submitting()">
        @if (submitting()) {
          <mat-spinner diameter="20" />
        } @else {
          Continuer
        }
      </button>
    </form>
  `,
  styles: [
    `
      .pj-field {
        margin-top: 24px;
      }
      .pj-error {
        color: var(--c-negative);
      }
      .pj-submit {
        width: 100%;
        margin-top: 24px;
      }
    `,
  ],
})
export class PoolJoinPage {
  private readonly poolService = inject(PoolService);
  private readonly router = inject(Router);

  code = '';
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  lookup(): void {
    const code = this.code.trim();
    if (!code || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.poolService.getByCode(code).subscribe({
      next: (r) => this.router.navigate(['/pay/pools', r.data.id], { replaceUrl: true }),
      error: (e) => {
        this.error.set(extractErrorMessage(e));
        this.submitting.set(false);
      },
    });
  }
}
