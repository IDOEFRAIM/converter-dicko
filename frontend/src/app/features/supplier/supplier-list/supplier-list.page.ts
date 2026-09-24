import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SupplierService } from '../../../core/services/supplier.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { SupplierSummary } from '../../../core/models/supplier.model';
import { PURPOSE_LABELS } from '../../../core/models/common.model';
import { BENEFICIARY_TYPE_LABELS } from '../../../core/models/order.model';

type SupplierFilter = 'ALL' | 'FAVORITES' | 'INACTIVE';

/**
 * Carnet de fournisseurs. Trois vues : tous les fournisseurs actifs, les favoris,
 * les fournisseurs desactives. Aucune suppression : la desactivation est logique
 * (l'historique des ordres deja crees reste intact cote backend).
 */
@Component({
  selector: 'app-supplier-list-page',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './supplier-list.page.html',
  styleUrl: './supplier-list.page.scss',
})
export class SupplierListPage implements OnInit {
  private readonly supplierService = inject(SupplierService);
  private readonly notification = inject(NotificationService);

  readonly suppliers = signal<SupplierSummary[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly filter = signal<SupplierFilter>('ALL');

  readonly typeLabels = BENEFICIARY_TYPE_LABELS;
  readonly purposeLabels = PURPOSE_LABELS;

  /**
   * Recherche cote client sur la page deja chargee (au plus 50 fournisseurs, section 9) : aucun
   * appel backend supplementaire, le backend n'exposant pas de recherche plein texte sur ce
   * endpoint. Voir le rapport de refonte pour le detail du gap identifie.
   */
  readonly searchControl = new FormControl('', { nonNullable: true });
  private readonly searchTerm = toSignal(this.searchControl.valueChanges, { initialValue: '' });

  readonly filteredSuppliers = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) {
      return this.suppliers();
    }
    return this.suppliers().filter((s) => s.displayName.toLowerCase().includes(term));
  });

  readonly emptyTitle = computed(() => {
    switch (this.filter()) {
      case 'FAVORITES':
        return 'Aucun fournisseur favori';
      case 'INACTIVE':
        return 'Aucun fournisseur desactive';
      default:
        return 'Aucun fournisseur enregistre';
    }
  });

  ngOnInit(): void {
    this.load();
  }

  setFilter(filter: SupplierFilter): void {
    if (filter === this.filter()) {
      return;
    }
    this.filter.set(filter);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    const current = this.filter();
    const request$ =
      current === 'FAVORITES'
        ? this.supplierService.listFavorites(0, 50)
        : this.supplierService.list(0, 50, current === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE');

    request$.subscribe({
      next: (response) => {
        this.suppliers.set(response.data.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  toggleFavorite(supplier: SupplierSummary, event: Event): void {
    event.stopPropagation();
    event.preventDefault();
    this.supplierService.setFavorite(supplier.id, !supplier.favorite).subscribe({
      next: (response) => {
        this.suppliers.update((list) =>
          list.map((s) => (s.id === supplier.id ? { ...s, favorite: response.data.favorite } : s)),
        );
        if (this.filter() === 'FAVORITES' && !response.data.favorite) {
          this.suppliers.update((list) => list.filter((s) => s.id !== supplier.id));
        }
      },
      error: (error) => this.notification.error(extractErrorMessage(error)),
    });
  }
}
