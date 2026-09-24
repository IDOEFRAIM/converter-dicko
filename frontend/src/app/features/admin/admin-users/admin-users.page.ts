import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { AdminUserService } from '../../../core/services/admin-user.service';
import { AdminUserSummary, UserAccountStatus } from '../../../core/models/admin-user.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

/** Liste des comptes clients (retour client : "l'admin n'arrive pas a voir les detail des
 * different fournisseur pour chaque utilisateur") -- point d'entree vers le detail d'un compte,
 * ou son carnet de fournisseurs est desormais consultable (voir AdminUserDetailPage). */
@Component({
  selector: 'app-admin-users-page',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-users.page.html',
  styleUrl: './admin-users.page.scss',
})
export class AdminUsersPage implements OnInit {
  private readonly adminUserService = inject(AdminUserService);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly selectedStatus = signal<UserAccountStatus | null>(null);
  readonly users = signal<AdminUserSummary[]>([]);
  readonly loading = signal(true);
  readonly displayedColumns = ['fullName', 'phone', 'status', 'kycVerified', 'orderCount', 'totalAmountCfa', 'actions'];

  ngOnInit(): void {
    this.load();
    this.searchControl.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.load());
  }

  onStatusChange(status: UserAccountStatus | null): void {
    this.selectedStatus.set(status);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.adminUserService.list(this.selectedStatus(), this.searchControl.value.trim() || null, 0, 30).subscribe({
      next: (response) => {
        this.users.set(response.data.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
