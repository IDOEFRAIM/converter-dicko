import { AppBarActionsDirective } from '../../../shared/directives/app-bar-actions.directive';
import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Pool, poolProgress } from '../../../core/models/pool.model';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { PoolService } from '../../../core/services/pool.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

/** "Mes Ruees" — copie de `MyPoolsPage` (mobile). */
@Component({
  selector: 'app-my-pools-page',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-template appBarActions>
      <a mat-icon-button routerLink="/pay/pools/join" aria-label="Rejoindre avec un code" title="Rejoindre avec un code">
        <mat-icon fontSet="material-icons-outlined">qr_code</mat-icon>
      </a>
    </ng-template>
    <div class="screen">
      <div class="surface-paper pool-explainer">
        <mat-icon>bolt</mat-icon>
        <p class="t-caption">
          Groupez vos transferts avec d'autres : objectif atteint a temps = reduction pour chacun
          sur son prochain transfert.
        </p>
      </div>

      <a mat-flat-button routerLink="/pay/pools/new" class="pool-cta">
        <mat-icon>bolt</mat-icon>
        Lancer une Ruee
      </a>

      <div class="pool-list">
        @if (loading()) {
          <div class="m-loading"><mat-spinner diameter="28" /></div>
        } @else if (error() && pools().length === 0) {
          <div class="m-empty">
            <mat-icon>error_outline</mat-icon>
            <p class="t-body-strong">{{ error() }}</p>
            <button mat-button type="button" (click)="load()">Reessayer</button>
          </div>
        } @else if (pools().length === 0) {
          <div class="m-empty">
            <mat-icon fontSet="material-icons-outlined">groups</mat-icon>
            <p class="t-body-strong">Aucune Ruee</p>
            <p class="t-caption">Lancez-en une ou rejoignez-en une.</p>
          </div>
        } @else {
          @if (active().length) {
            <h2 class="t-eyebrow pool-group-title">En cours</h2>
            @for (p of active(); track p.id) {
              <ng-container *ngTemplateOutlet="tile; context: { $implicit: p }" />
            }
          }
          @if (closed().length) {
            <h2 class="t-eyebrow pool-group-title">Terminees</h2>
            @for (p of closed(); track p.id) {
              <ng-container *ngTemplateOutlet="tile; context: { $implicit: p }" />
            }
          }
        }
      </div>
    </div>

    <ng-template #tile let-p>
      <a [routerLink]="['/pay/pools', p.id]" class="surface-paper pool-tile pressable">
        <span class="pool-tile__row">
          <span class="list-card__main">
            <span class="t-body-strong">Code {{ p.code }}</span>
            <span class="t-caption">
              {{ p.currentAmountXof | money: 'XOF' }} / {{ p.targetAmountXof | money: 'XOF' }} ·
              {{ p.participantCount }} participant(s)
            </span>
          </span>
          <app-status-badge [status]="p.status" />
        </span>
        <span class="pool-bar"
          ><span class="pool-bar__fill" [style.width.%]="progress(p) * 100"></span
        ></span>
      </a>
    </ng-template>
  `,
  styles: [
    `
      .pool-explainer {
        display: flex;
        gap: 8px;
        padding: 12px;
      }
      .pool-explainer .mat-icon {
        color: var(--c-keyline);
        font-size: 20px;
        width: 20px;
        height: 20px;
        flex: 0 0 auto;
      }
      .pool-cta {
        width: 100%;
        margin-top: 16px;
      }
      .pool-cta--secondary {
        margin-top: 8px;
      }
      .pool-list {
        margin-top: 24px;
      }
      .pool-group-title {
        margin: 0 0 8px;
      }
      .pool-group-title ~ .pool-group-title {
        margin-top: 16px;
      }
      .pool-tile {
        display: block;
        padding: 12px;
        margin-bottom: 8px;
        color: inherit;
        text-decoration: none;
      }
      .pool-tile__row {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .pool-bar {
        display: block;
        height: 3px;
        margin-top: 8px;
        border-radius: 999px;
        background: var(--c-paper-edge);
        overflow: hidden;
      }
      .pool-bar__fill {
        display: block;
        height: 100%;
        background: var(--c-keyline);
      }
    `,
  ],
})
export class MyPoolsPage implements OnInit {
  private readonly poolService = inject(PoolService);

  readonly pools = signal<Pool[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly active = computed(() => this.pools().filter((p) => p.status === 'ACTIVE'));
  readonly closed = computed(() => this.pools().filter((p) => p.status !== 'ACTIVE'));
  readonly progress = poolProgress;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.poolService.mine().subscribe({
      next: (r) => {
        this.pools.set(r.data.content);
        this.error.set(null);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(extractErrorMessage(e));
        this.loading.set(false);
      },
    });
  }
}
