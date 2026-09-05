import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { InboxNotificationService } from '../../core/services/inbox-notification.service';
import { extractErrorMessage } from '../../core/services/api-error.util';
import { InboxNotification, notificationIcon } from '../../core/models/inbox-notification.model';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

/** Notifications internes du client, les plus recentes en premier. */
@Component({
  selector: 'app-notifications-page',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule, EmptyStateComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './notifications.page.html',
  styleUrl: './notifications.page.scss',
})
export class NotificationsPage implements OnInit {
  private readonly notificationService = inject(InboxNotificationService);

  readonly notifications = signal<InboxNotification[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.notificationService.list(0, 30).subscribe({
      next: (response) => {
        this.notifications.set(response.data.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  icon(type: string): string {
    return notificationIcon(type);
  }

  markRead(notification: InboxNotification): void {
    if (notification.readAt) {
      return;
    }
    this.notificationService.markRead(notification.id).subscribe({
      next: (response) => {
        this.notifications.set(this.notifications().map((n) => (n.id === response.data.id ? response.data : n)));
      },
    });
  }
}
