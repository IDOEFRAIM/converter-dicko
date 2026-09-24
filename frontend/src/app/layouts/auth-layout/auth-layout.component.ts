import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Ecrans d'authentification — memes ecrans que l'app mobile (LoginPage / RegisterPage) :
 * pas de carte flottante, fond ivoire, contenu aligne a gauche dans une colonne telephone.
 */
@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth-frame">
      <main class="auth-frame__body">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
        min-height: 100dvh;
        background: var(--c-ivory-dim);
      }
      .auth-frame {
        max-width: 600px;
        min-height: 100vh;
        min-height: 100dvh;
        margin: 0 auto;
      }
      @media (min-width: 600px) {
        .auth-frame {
          box-shadow: 0 0 0 1px var(--c-outline), 0 20px 60px rgba(27, 50, 94, 0.08);
        }
      }
      .auth-frame__body {
        padding: calc(32px + env(safe-area-inset-top, 0px)) 24px 32px;
      }
    `,
  ],
})
export class AuthLayoutComponent {}
