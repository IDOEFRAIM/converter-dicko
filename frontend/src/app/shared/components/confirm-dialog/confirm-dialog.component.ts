import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Si renseigne, un champ texte obligatoire est affiche (motif d'annulation/rejet) et sa valeur est retournee. */
  requireReasonLabel?: string;
}

/** Boite de dialogue de confirmation reutilisable, avec motif textuel optionnel. */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
      @if (data.requireReasonLabel) {
        <mat-form-field appearance="outline" style="width: 100%">
          <mat-label>{{ data.requireReasonLabel }}</mat-label>
          <textarea matInput [formControl]="reasonControl" rows="3"></textarea>
          @if (reasonControl.invalid && reasonControl.touched) {
            <mat-error>Ce champ est obligatoire.</mat-error>
          }
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close(null)">{{ data.cancelLabel ?? 'Annuler' }}</button>
      <button mat-flat-button color="primary" (click)="confirm()">
        {{ data.confirmLabel ?? 'Confirmer' }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialogComponent {
  readonly dialogRef = inject<MatDialogRef<ConfirmDialogComponent, string | true | null>>(MatDialogRef);
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);

  readonly reasonControl = new FormControl('', { nonNullable: true, validators: [Validators.required] });

  confirm(): void {
    if (this.data.requireReasonLabel) {
      if (this.reasonControl.invalid) {
        this.reasonControl.markAsTouched();
        return;
      }
      this.dialogRef.close(this.reasonControl.value);
      return;
    }
    this.dialogRef.close(true);
  }
}

/** Ouvre {@link ConfirmDialogComponent} avec un typage de retour simple. */
export function openConfirmDialog(dialog: MatDialog, data: ConfirmDialogData) {
  return dialog.open(ConfirmDialogComponent, { data, width: '420px' }).afterClosed();
}
