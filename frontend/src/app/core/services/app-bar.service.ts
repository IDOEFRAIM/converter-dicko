import { Injectable, TemplateRef, signal } from '@angular/core';

/**
 * Barre d'application de l'espace client (ClientLayoutComponent) — equivalent des
 * `AppBar(title:, actions:)` de chaque ecran mobile : une page peut y poser un titre
 * dynamique (ex. le nom du fournisseur) et ses propres boutons (favori, modifier,
 * actualiser...). Tout est remis a zero a chaque changement d'ecran.
 */
@Injectable({ providedIn: 'root' })
export class AppBarService {
  readonly title = signal<string | null>(null);
  readonly actions = signal<TemplateRef<unknown> | null>(null);

  reset(): void {
    this.title.set(null);
    this.actions.set(null);
  }
}
