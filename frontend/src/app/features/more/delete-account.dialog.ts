import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { extractErrorMessage } from '../../core/services/api-error.util';
import { AuthService } from '../../core/services/auth.service';

export interface DeleteAccountDialogData {
  requiresPassword: boolean;
}

/** Copie de `DeleteAccountDialog` (mobile) : meme texte, meme re-confirmation par mot de passe. */
@Component({
  selector: 'app-delete-account-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Supprimer votre compte ?</h2>
    <mat-dialog-content>
      <p class="t-body">
        Cette action est definitive : vos informations personnelles (nom, telephone, piece
        d'identite) seront effacees. Vos transferts deja effectues restent visibles par notre
        equipe, comme l'exige la reglementation.
      </p>
      <p class="t-body">
        Impossible si un transfert est encore en cours : terminez-le, annulez-le ou attendez son
        rejet d'abord.
      </p>
      @if (data.requiresPassword) {
        <mat-form-field appearance="outline" class="form__field">
          <mat-label>Confirmez votre mot de passe</mat-label>
          <input
            matInput
            type="password"
            autocomplete="current-password"
            [(ngModel)]="password"
            (keydown.enter)="confirm()"
          />
        </mat-form-field>
      }
      @if (errorMessage()) {
        <p class="t-body delete-error">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="submitting()" mat-dialog-close>Annuler</button>
      <button
        mat-button
        type="button"
        class="delete-confirm"
        [disabled]="submitting()"
        (click)="confirm()"
      >
        @if (submitting()) {
          <mat-spinner diameter="16" />
        } @else {
          Supprimer
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .delete-error {
        color: var(--c-negative);
      }
      .delete-confirm:not([disabled]) {
        color: var(--c-negative) !important;
      }
      p + p {
        margin-top: 12px;
      }
    `,
  ],
})
export class DeleteAccountDialog {
  readonly data = inject<DeleteAccountDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<DeleteAccountDialog, boolean>);
  private readonly auth = inject(AuthService);

  password = '';
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  confirm(): void {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth.deleteAccount(this.data.requiresPassword ? this.password : null).subscribe({
      next: () => this.dialogRef.close(true),
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.submitting.set(false);
      },
    });
  }
}
