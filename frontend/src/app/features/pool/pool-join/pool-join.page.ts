import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PoolService } from '../../../core/services/pool.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';

/**
 * Saisie d'un code de Ruee recu par un ami (mission "differenciation marketing", Lot 3) — resout
 * le code puis navigue vers le detail. Ne rejoint jamais automatiquement ici : consulter une
 * Ruee et la rejoindre restent deux actions distinctes (memes que sur mobile).
 */
@Component({
  selector: 'app-pool-join-page',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pool-join.page.html',
  styleUrl: './pool-join.page.scss',
})
export class PoolJoinPage {
  private readonly poolService = inject(PoolService);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = new FormGroup({
    code: new FormControl('', { nonNullable: true }),
  });

  onCodeInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const upper = input.value.toUpperCase();
    if (upper !== input.value) {
      this.form.controls.code.setValue(upper);
    }
  }

  lookup(): void {
    const code = this.form.controls.code.value.trim();
    if (!code || this.submitting()) return;
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.poolService.getByCode(code).subscribe({
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
