import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { PwaInstallService } from '../../../core/services/pwa-install.service';

/**
 * Bannière "Installer l'application" (mission "blocages Apple/Meta" oct. 2026) : le site devient
 * le canal de secours pour les utilisateurs qui ne peuvent pas installer l'app native. Placee au
 * niveau racine (voir app.html) pour rester visible avant meme la connexion.
 */
@Component({
  selector: 'app-install-app-banner',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (pwaInstall.shouldOfferInstall) {
      <div class="install-banner">
        <mat-icon class="install-banner__icon">install_mobile</mat-icon>
        <div class="install-banner__text">
          <p class="install-banner__title">Installer YUAN PAY BF</p>
          @if (pwaInstall.isIosSafari() && !pwaInstall.canPromptInstall()) {
            <p class="install-banner__hint">
              Appuyez sur <mat-icon inline class="install-banner__inline-icon">ios_share</mat-icon>
              puis "Sur l'ecran d'accueil".
            </p>
          } @else {
            <p class="install-banner__hint">Acces rapide, plein ecran, comme une application.</p>
          }
        </div>
        @if (pwaInstall.canPromptInstall()) {
          <button mat-flat-button class="brand-button install-banner__action" (click)="install()">Installer</button>
        }
        <button mat-icon-button aria-label="Fermer" (click)="pwaInstall.dismiss()">
          <mat-icon>close</mat-icon>
        </button>
      </div>
    }
  `,
  styles: [
    `
      .install-banner {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.625rem 0.875rem;
        background: var(--brand-navy, #1b325e);
        color: #fff;
      }
      .install-banner__icon {
        flex-shrink: 0;
      }
      .install-banner__text {
        flex: 1;
        min-width: 0;
      }
      .install-banner__title {
        margin: 0;
        font-weight: 700;
        font-size: 0.875rem;
      }
      .install-banner__hint {
        margin: 0.125rem 0 0;
        font-size: 0.75rem;
        opacity: 0.85;
        display: flex;
        align-items: center;
        gap: 0.25rem;
        flex-wrap: wrap;
      }
      .install-banner__inline-icon {
        font-size: 1rem;
        height: 1rem;
        width: 1rem;
        vertical-align: text-bottom;
      }
      .install-banner__action {
        flex-shrink: 0;
        color: var(--brand-navy, #1b325e) !important;
        background: #fff !important;
      }
      button[mat-icon-button] {
        color: #fff;
        flex-shrink: 0;
      }
    `,
  ],
})
export class InstallAppBannerComponent {
  readonly pwaInstall = inject(PwaInstallService);

  install(): void {
    this.pwaInstall.promptInstall();
  }
}
