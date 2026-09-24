import { Directive, OnDestroy, OnInit, TemplateRef, inject } from '@angular/core';
import { AppBarService } from '../../core/services/app-bar.service';

/**
 * `<ng-template appBarActions>...</ng-template>` : projette ces boutons dans la barre
 * d'application (a droite du titre, avant la cloche), comme `AppBar.actions` sur mobile.
 */
@Directive({ selector: '[appBarActions]', standalone: true })
export class AppBarActionsDirective implements OnInit, OnDestroy {
  private readonly template = inject(TemplateRef<unknown>);
  private readonly appBar = inject(AppBarService);

  ngOnInit(): void {
    // Hors du cycle de detection courant : la barre appartient au layout parent.
    queueMicrotask(() => this.appBar.actions.set(this.template));
  }

  ngOnDestroy(): void {
    if (this.appBar.actions() === this.template) {
      this.appBar.actions.set(null);
    }
  }
}
