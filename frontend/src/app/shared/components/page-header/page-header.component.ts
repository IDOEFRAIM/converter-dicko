import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** En-tete de page uniforme : titre + sous-titre optionnel + zone d'actions projetee. */
@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <div>
        <h1 class="page-header__title">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="page-header__subtitle">{{ subtitle() }}</p>
        }
      </div>
      <div class="page-header__actions">
        <ng-content />
      </div>
    </header>
  `,
  styles: [
    `
      .page-header {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        justify-content: space-between;
        align-items: flex-start;
        margin-bottom: 1.5rem;
      }
      .page-header__title {
        font-size: 1.375rem;
        font-weight: 700;
        margin: 0;
        color: var(--brand-navy, #1b325e);
      }
      .page-header__subtitle {
        margin: 0.25rem 0 0;
        color: var(--mat-sys-on-surface-variant, #616161);
        font-size: 0.9rem;
      }
      /* Espace client : le titre est deja dans la barre d'application (comme l'app
         mobile) — seules les actions de la page restent affichees ici. */
      :host-context(.app-frame) .page-header > div:first-child {
        display: none;
      }
      :host-context(.app-frame) .page-header {
        margin-bottom: 16px;
        justify-content: flex-end;
      }
      :host-context(.app-frame) .page-header__actions:empty {
        display: none;
      }
      :host-context(.app-frame) .page-header:has(.page-header__actions:empty) {
        display: none;
      }
      .page-header__actions {
        display: flex;
        gap: 0.5rem;
        flex-wrap: wrap;
      }
    `,
  ],
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>('');
}
