import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { PwaInstallService } from '../../../core/pwa/pwa-install.service';

/**
 * Bandeau "Installer l'application" — pose au-dessus de la barre de navigation de
 * l'espace client, donc visible des la connexion. Trois variantes selon l'appareil
 * (voir {@link PwaInstallService.mode}) : bouton d'installation natif, ou
 * instructions pas-a-pas quand le navigateur n'offre pas d'API (iOS Safari...).
 */
@Component({
  selector: 'app-install-banner',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (pwa.bannerVisible()) {
      <aside class="install surface-lacquer" role="dialog" aria-labelledby="install-title">
        <button type="button" class="install__close" (click)="pwa.dismiss()" aria-label="Fermer">
          <mat-icon>close</mat-icon>
        </button>
        <div class="install__head">
          <img class="install__icon" src="icons/icon-192.png" alt="" width="48" height="48" />
          <div>
            <p id="install-title" class="install__title">Installer YUAN PAY</p>
            <p class="install__text">
              Ouvrez le service en un geste depuis votre ecran d'accueil, comme l'application.
            </p>
          </div>
        </div>

        @switch (pwa.mode()) {
          @case ('prompt') {
            <div class="install__actions">
              <button mat-button type="button" class="install__later" (click)="pwa.dismiss()">
                Plus tard
              </button>
              <button mat-flat-button type="button" class="install__cta" (click)="pwa.install()">
                <mat-icon>download</mat-icon>
                Installer
              </button>
            </div>
          }
          @case ('ios') {
            <ol class="install__steps">
              <li>
                Touchez <mat-icon class="install__inline-icon">ios_share</mat-icon>
                <strong>Partager</strong> dans la barre de Safari
              </li>
              <li>
                Choisissez <mat-icon class="install__inline-icon">add_box</mat-icon>
                <strong>Sur l'ecran d'accueil</strong>
              </li>
              <li>Validez avec <strong>Ajouter</strong></li>
            </ol>
          }
          @case ('manual') {
            <ol class="install__steps">
              <li>
                Ouvrez le menu <mat-icon class="install__inline-icon">more_vert</mat-icon> du
                navigateur
              </li>
              <li>
                Choisissez <strong>Installer l'application</strong> ou
                <strong>Ajouter a l'ecran d'accueil</strong>
              </li>
            </ol>
          }
        }
      </aside>
    }
  `,
  styles: [
    `
      .install {
        pointer-events: auto;
        position: relative;
        margin-bottom: 12px;
        padding: 16px;
        animation: install-in 280ms cubic-bezier(0.22, 1, 0.36, 1);
      }
      @keyframes install-in {
        from {
          transform: translateY(16px);
          opacity: 0;
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .install {
          animation: none;
        }
      }
      .install__close {
        position: absolute;
        top: 8px;
        right: 8px;
        display: flex;
        padding: 4px;
        border: 0;
        background: none;
        color: var(--c-on-lacquer-muted);
        cursor: pointer;
      }
      .install__head {
        display: flex;
        gap: 12px;
        align-items: center;
        padding-right: 24px;
      }
      .install__icon {
        border-radius: 12px;
        flex: 0 0 auto;
        box-shadow: 0 0 0 1px rgba(199, 165, 75, 0.55);
      }
      .install__title {
        margin: 0 0 2px;
        font-size: 17px;
        font-weight: 700;
        color: var(--c-on-lacquer);
      }
      .install__text {
        margin: 0;
        font-size: 13px;
        line-height: 1.35;
        color: var(--c-on-lacquer-muted);
      }
      .install__actions {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        margin-top: 12px;
      }
      .install__later.mat-mdc-button {
        color: var(--c-on-lacquer-muted);
      }
      .install__cta.mat-mdc-unelevated-button {
        --mat-button-filled-container-height: 44px;
        background: var(--c-keyline);
        color: var(--c-lacquer);
      }
      .install__steps {
        margin: 12px 0 0;
        padding-left: 20px;
        font-size: 13px;
        line-height: 1.6;
        color: var(--c-on-lacquer);
      }
      .install__inline-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        vertical-align: -4px;
        color: var(--c-keyline);
      }
    `,
  ],
})
export class InstallBannerComponent {
  readonly pwa = inject(PwaInstallService);
}
