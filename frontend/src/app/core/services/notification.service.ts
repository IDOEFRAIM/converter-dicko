import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Point unique d'affichage des messages de succes/erreur transitoires. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(private readonly snackBar: MatSnackBar) {}

  success(message: string): void {
    this.snackBar.open(message, 'Fermer', { duration: 4000, panelClass: 'notification-success' });
  }

  error(message: string): void {
    this.snackBar.open(message, 'Fermer', { duration: 6000, panelClass: 'notification-error' });
  }
}
