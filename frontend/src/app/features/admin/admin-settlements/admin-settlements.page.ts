import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { AdminSettlementService } from '../../../core/services/admin-settlement.service';
import { Settlement } from '../../../core/models/settlement.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

@Component({
  selector: 'app-admin-settlements-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-settlements.page.html',
  styleUrl: './admin-settlements.page.scss',
})
export class AdminSettlementsPage implements OnInit {
  private readonly adminSettlementService = inject(AdminSettlementService);

  readonly settlements = signal<Settlement[]>([]);
  readonly loading = signal(true);
  readonly displayedColumns = ['beneficiaryFullName', 'method', 'amountCny', 'createdAt', 'actions'];

  ngOnInit(): void {
    this.adminSettlementService.pending(0, 30).subscribe({
      next: (response) => {
        this.settlements.set(response.data.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
