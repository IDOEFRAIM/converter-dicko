import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';

export interface DeleteAccountDialogData {
  /** Faux pour un compte cree via Google Sign-In (voir CurrentUser.hasPassword) : aucun champ
   * mot de passe n'a alors de sens, le backend l'ignore de toute facon. */
  requiresPassword: boolean;
}

/**
 * Confirmation de suppression de compte (retour client : "est-ce que l'utilisateur a la
 * possibilite de supprimer ses donnees dans l'application ?") — parite web de `DeleteAccountDialog`
 * (mobile). Un composant dedie plutot que `ConfirmDialogComponent` : le mot de passe doit rester
 * masque (`type="password"`) et l'erreur backend (transfert en cours, mot de passe incorrect) doit
 * s'afficher SANS fermer la boite de dialogue, comme sur mobile.
 */
@Component({
  selector: 'app-delete-account-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Supprimer votre compte ?</h2>
    <mat-dialog-content>
      <p>
        Cette action est definitive : vos informations personnelles (nom, telephone, piece
        d'identite) seront effacees. Vos transferts deja effectues restent visibles par notre
        equipe, comme l'exige la reglementation.
      </p>
      <p>
        Impossible si un transfert est encore en cours : terminez-le, annulez-le ou attendez son
        rejet d'abord.
      </p>
      @if (data.requiresPassword) {
        <mat-form-field appearance="outline" style="width: 100%">
          <mat-label>Confirmez votre mot de passe</mat-label>
          <input
            matInput
            type="password"
            [formControl]="passwordControl"
            autofocus
            (keydown.enter)="confirm()"
          />
        </mat-form-field>
      }
      @if (errorMessage()) {
        <p class="form-notice form-notice--error">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [disabled]="submitting()" (click)="dialogRef.close(false)">Annuler</button>
      <button mat-button color="warn" [disabled]="submitting()" (click)="confirm()">
        @if (submitting()) {
          <mat-spinner diameter="18" />
        } @else {
          Supprimer
        }
      </button>
    </mat-dialog-actions>
  `,
})
export class DeleteAccountDialogComponent {
  readonly dialogRef = inject<MatDialogRef<DeleteAccountDialogComponent, boolean>>(MatDialogRef);
  readonly data = inject<DeleteAccountDialogData>(MAT_DIALOG_DATA);
  private readonly authService = inject(AuthService);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly passwordControl = new FormControl('', { nonNullable: true });

  confirm(): void {
    if (this.submitting()) return;
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.authService.deleteAccount(this.data.requiresPassword ? this.passwordControl.value : null).subscribe({
      next: () => this.dialogRef.close(true),
      error: (error) => {
        this.submitting.set(false);
        this.errorMessage.set(extractErrorMessage(error));
      },
    });
  }
}

export function openDeleteAccountDialog(dialog: MatDialog, data: DeleteAccountDialogData) {
  return dialog.open(DeleteAccountDialogComponent, { data, width: '420px' }).afterClosed();
}
