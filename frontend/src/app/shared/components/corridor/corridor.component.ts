import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Langage visuel recurrent du produit : le corridor Burkina Faso -> Chine. Volontairement sobre
 * (drapeaux + une ligne), jamais un widget lourd repete a l'identique partout — chaque page
 * l'utilise a sa maniere (banniere sur le dashboard, version compacte sur la page devis).
 *
 * <p>Les libelles restent generiques ("Burkina Faso" / "Chine") par defaut : aucune ville n'est
 * inventee quand le backend n'en fournit pas pour l'utilisateur courant.
 */
@Component({
  selector: 'app-corridor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="corridor" [class.corridor--compact]="compact()">
      <span class="corridor__side">
        <span class="corridor__flag" aria-hidden="true">🇧🇫</span>
        <span class="corridor__label">{{ leftLabel() }}</span>
      </span>
      <span class="corridor__line" aria-hidden="true"></span>
      <span class="corridor__side">
        <span class="corridor__label">{{ rightLabel() }}</span>
        <span class="corridor__flag" aria-hidden="true">🇨🇳</span>
      </span>
    </div>
  `,
  styles: [
    `
      .corridor {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        width: 100%;
      }
      .corridor__side {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        flex: 0 0 auto;
        font-size: 0.8125rem;
        font-weight: 700;
        letter-spacing: 0.01em;
        white-space: nowrap;
      }
      .corridor__flag {
        font-size: 1.0625rem;
        line-height: 1;
      }
      .corridor__line {
        color: var(--accent, currentColor);
        flex: 1 1 auto;
        height: 2px;
        min-width: 1.5rem;
        background: repeating-linear-gradient(
          90deg,
          currentColor 0 10px,
          transparent 10px 14px
        );
        position: relative;
        opacity: 0.75;
      }
      .corridor__line::after {
        content: '';
        position: absolute;
        right: -1px;
        top: 50%;
        width: 0;
        height: 0;
        transform: translateY(-50%);
        border-top: 5px solid transparent;
        border-bottom: 5px solid transparent;
        border-left: 7px solid currentColor;
      }
      .corridor--compact {
        gap: 0.375rem;
      }
      .corridor--compact .corridor__side {
        font-size: 0.6875rem;
      }
      .corridor--compact .corridor__flag {
        font-size: 0.8125rem;
      }
    `,
  ],
})
export class CorridorComponent {
  readonly leftLabel = input('Burkina Faso');
  readonly rightLabel = input('Chine');
  readonly compact = input(false);
}
