import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Etat vide reutilisable — listes sans resultat, historique vide, etc. */
@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty-state">
      <p class="empty-state__title">{{ title() }}</p>
      @if (description()) {
        <p class="empty-state__description">{{ description() }}</p>
      }
      <div class="empty-state__action">
        <ng-content />
      </div>
    </div>
  `,
  styles: [
    `
      .empty-state {
        text-align: center;
        padding: 3rem 1.5rem;
        color: var(--mat-sys-on-surface-variant, #616161);
      }
      .empty-state__title {
        font-size: 1rem;
        font-weight: 600;
        margin: 0 0 0.25rem;
      }
      .empty-state__description {
        font-size: 0.875rem;
        margin: 0;
      }
      .empty-state__action:empty {
        display: none;
      }
      .empty-state__action {
        margin-top: 1rem;
      }
    `,
  ],
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly description = input<string>('');
}
