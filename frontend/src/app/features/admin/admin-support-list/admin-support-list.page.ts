import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { AdminSupportService } from '../../../core/services/support.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { SupportThreadSummary } from '../../../core/models/support.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';

/** Boite de reception SAV : un fil par utilisateur, tries par activite la plus recente. */
@Component({
  selector: 'app-admin-support-list-page',
  standalone: true,
  imports: [DatePipe, MatIconModule, MatProgressSpinnerModule, MatTableModule, PageHeaderComponent, EmptyStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-support-list.page.html',
  styleUrl: './admin-support-list.page.scss',
})
export class AdminSupportListPage implements OnInit {
  private readonly supportService = inject(AdminSupportService);
  private readonly router = inject(Router);

  readonly threads = signal<SupportThreadSummary[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly displayedColumns = ['user', 'lastMessage', 'lastMessageAt'];

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.supportService.listThreads(0, 50).subscribe({
      next: (response) => {
        this.threads.set(response.data.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  open(thread: SupportThreadSummary): void {
    this.router.navigate(['/admin/support', thread.userId]);
  }
}
