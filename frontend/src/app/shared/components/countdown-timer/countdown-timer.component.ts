import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';

/**
 * Compte a rebours visuel jusqu'a {@code expiresAt}. Purement
 * cosmetique : la seule verite sur l'expiration reste le backend
 * (verifiee a chaque appel accept/cancel) — ce composant ne fait
 * qu'informer visuellement l'utilisateur avant qu'il n'agisse.
 */
@Component({
  selector: 'app-countdown-timer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!expired()) {
      <span class="countdown" [class.countdown--urgent]="urgent()">
        Expire dans {{ display() }}
      </span>
    } @else {
      <span class="countdown countdown--expired">Devis expire</span>
    }
  `,
  styles: [
    `
      .countdown {
        font-variant-numeric: tabular-nums;
        font-weight: 600;
        color: var(--mat-sys-primary, #1565c0);
      }
      .countdown--urgent {
        color: #c62828;
      }
      .countdown--expired {
        color: #c62828;
        font-weight: 600;
      }
    `,
  ],
})
export class CountdownTimerComponent {
  readonly expiresAt = input.required<string>();
  readonly expiredChange = output<void>();

  private readonly destroyRef = inject(DestroyRef);
  private readonly now = signal(Date.now());
  private hasEmittedExpiry = false;

  readonly expired = signal(false);
  readonly urgent = signal(false);
  readonly display = signal('');

  constructor() {
    const intervalId = setInterval(() => this.now.set(Date.now()), 1000);
    this.destroyRef.onDestroy(() => clearInterval(intervalId));

    effect(() => {
      const remainingMs = new Date(this.expiresAt()).getTime() - this.now();
      const isExpired = remainingMs <= 0;
      this.expired.set(isExpired);
      this.urgent.set(!isExpired && remainingMs < 5 * 60 * 1000);
      this.display.set(isExpired ? '0:00' : formatDuration(remainingMs));

      if (isExpired && !this.hasEmittedExpiry) {
        this.hasEmittedExpiry = true;
        this.expiredChange.emit();
      }
    });
  }
}

function formatDuration(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}
