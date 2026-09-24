import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from '../../core/services/auth.service';
import { InboxNotificationService } from '../../core/services/inbox-notification.service';
import { NotificationService } from '../../core/services/notification.service';
import { openDeleteAccountDialog } from '../../shared/components/delete-account-dialog/delete-account-dialog.component';

const UNREAD_POLL_INTERVAL_MS = 30_000;

@Component({
  selector: 'app-client-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatBadgeModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './client-layout.component.html',
  styleUrl: './client-layout.component.scss',
})
export class ClientLayoutComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notificationService = inject(InboxNotificationService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly currentUser = this.auth.currentUser;
  readonly unreadCount = signal(0);

  ngOnInit(): void {
    this.refreshUnreadCount();
    const intervalId = setInterval(() => this.refreshUnreadCount(), UNREAD_POLL_INTERVAL_MS);
    this.destroyRef.onDestroy(() => clearInterval(intervalId));
  }

  private refreshUnreadCount(): void {
    this.notificationService.unreadCount().subscribe({
      next: (response) => this.unreadCount.set(response.data.unreadCount),
      error: () => undefined,
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  deleteAccount(): void {
    const user = this.currentUser();
    if (!user) return;
    openDeleteAccountDialog(this.dialog, { requiresPassword: user.hasPassword }).subscribe((deleted) => {
      if (!deleted) return;
      // AuthService.deleteAccount() a deja purge la session (voir son tap()) -- il reste
      // seulement a rediriger, meme principe que logout() ci-dessus.
      this.router.navigate(['/login']);
      this.notification.success('Compte supprime.');
    });
  }
}
