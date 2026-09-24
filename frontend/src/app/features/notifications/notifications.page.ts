import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { InboxNotificationService } from '../../core/services/inbox-notification.service';
import { PushNotificationService } from '../../core/services/push-notification.service';
import { extractErrorMessage } from '../../core/services/api-error.util';
import { InboxNotification, notificationIcon } from '../../core/models/inbox-notification.model';

/** Notifications internes du client, les plus recentes en premier. */
@Component({
  selector: 'app-notifications-page',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './notifications.page.html',
  styleUrl: './notifications.page.scss',
})
export class NotificationsPage implements OnInit {
  private readonly notificationService = inject(InboxNotificationService);
  readonly push = inject(PushNotificationService);

  readonly notifications = signal<InboxNotification[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  /** null tant que la disponibilite cote serveur (cles VAPID configurees) n'est pas connue. */
  private readonly pushAvailable = signal<boolean | null>(null);
  private readonly vapidPublicKey = signal<string | null>(null);

  /** L'UI d'activation n'a de sens que si le navigateur ET le serveur supportent le push, et que
   * l'utilisateur n'a pas deja bloque les notifications dans les reglages du navigateur. */
  readonly showPushToggle = computed(
    () => this.push.supported && this.pushAvailable() === true && this.push.permission() !== 'denied',
  );

  ngOnInit(): void {
    this.load();
    this.loadPushConfig();
    this.push.refreshStatus();
  }

  private loadPushConfig(): void {
    if (!this.push.supported) return;
    this.push.config().subscribe({
      next: (response) => {
        this.pushAvailable.set(response.data.available);
        this.vapidPublicKey.set(response.data.vapidPublicKey);
      },
      error: () => this.pushAvailable.set(false),
    });
  }

  togglePush(): void {
    if (this.push.subscribed()) {
      this.push.unsubscribe();
      return;
    }
    const key = this.vapidPublicKey();
    if (!key) return;
    this.push.subscribe(key);
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
