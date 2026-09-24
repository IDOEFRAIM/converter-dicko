import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { PwaInstallService } from '../../core/pwa/pwa-install.service';
import { AuthService } from '../../core/services/auth.service';
import { DeleteAccountDialog, DeleteAccountDialogData } from './delete-account.dialog';

/** Pages legales deja en ligne — memes URL que `LegalLinks` (mobile). */
const LEGAL = {
  privacy: 'https://yuanpaybf.vercel.app/privacy',
  terms: 'https://yuanpaybf.vercel.app/cgu',
  eula: 'https://yuanpaybf.vercel.app/cluf',
} as const;

/**
 * Menu "Plus" — copie conforme de `MorePage` (mobile) : en-tete du compte, puis
 * sections MES TRANSFERTS / TAUX / MON COMPTE / LEGAL, puis la deconnexion.
 * Seul ajout propre au web : "Installer l'application" (PWA), tant qu'elle ne l'est pas.
 */
@Component({
  selector: 'app-more-page',
  standalone: true,
  imports: [RouterLink, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './more.page.html',
})
export class MorePage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  readonly pwa = inject(PwaInstallService);

  readonly user = this.auth.currentUser;
  readonly legal = LEGAL;
  readonly initial = computed(() => this.user()?.firstName?.[0]?.toUpperCase() ?? '?');
  readonly fullName = computed(() => {
    const user = this.user();
    return user ? `${user.firstName} ${user.lastName}`.trim() : '';
  });

  install(): void {
    if (this.pwa.mode() === 'prompt') {
      this.pwa.install();
    } else {
      this.pwa.show();
    }
  }

  deleteAccount(): void {
    const user = this.user();
    if (!user) {
      return;
    }
    this.dialog
      .open<DeleteAccountDialog, DeleteAccountDialogData, boolean>(DeleteAccountDialog, {
        data: { requiresPassword: user.hasPassword !== false },
        maxWidth: '560px',
      })
      .afterClosed()
      .subscribe((deleted) => {
        if (deleted) {
          this.router.navigate(['/login']);
        }
      });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
