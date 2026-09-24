import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MoneyPipe } from '../../pipes/money.pipe';

export interface TicketRow {
  label: string;
  value: string;
  copyable?: boolean;
}

/**
 * Ticket de transfert — copie de `TransferTicket` (mobile) : talon "vous envoyez /
 * le beneficiaire recoit", perforation doree, lignes de detail, note de bas de ticket.
 */
@Component({
  selector: 'app-transfer-ticket',
  standalone: true,
  imports: [MatIconModule, MoneyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ticket surface-paper">
      <div class="ticket__stubs">
        <p class="t-eyebrow">Vous envoyez</p>
        <p class="ticket__figure">{{ sendXof() | money: 'XOF' }}</p>
        <mat-icon class="ticket__arrow">arrow_downward</mat-icon>
        <p class="t-eyebrow">{{ receiveLabel() }}</p>
        <p class="ticket__figure ticket__figure--accent">
          {{ receiveApprox() ? '≈ ' : '' }}{{ receiveCny() | money: 'CNY' }}
        </p>
      </div>
      <div class="ticket__perforation" aria-hidden="true"></div>
      @if (rows().length) {
        <div class="ticket__rows">
          @for (row of rows(); track row.label) {
            @if (row.copyable) {
              <button
                type="button"
                class="ticket__row ticket__row--copy pressable"
                (click)="copy(row)"
              >
                <span class="t-caption">{{ row.label }}</span>
                <span class="ticket__value">{{ row.value }}</span>
                <mat-icon class="ticket__copy" [class.ticket__copy--ok]="copied() === row.label">
                  {{ copied() === row.label ? 'check' : 'content_copy' }}
                </mat-icon>
              </button>
            } @else {
              <div class="ticket__row">
                <span class="t-caption">{{ row.label }}</span>
                <span class="ticket__value">{{ row.value }}</span>
              </div>
            }
          }
        </div>
      }
      <div class="ticket__footnote t-caption"><ng-content /></div>
    </div>
  `,
  styles: [
    `
      .ticket {
        overflow: hidden;
      }
      .ticket__stubs {
        padding: 16px 16px 12px;
      }
      .ticket__figure {
        margin: 4px 0 0;
        font-size: 34px;
        font-weight: 800;
        letter-spacing: -0.9px;
        line-height: 1;
        font-variant-numeric: tabular-nums slashed-zero;
        color: var(--c-ink);
        overflow-wrap: anywhere;
      }
      .ticket__figure--accent {
        color: var(--accent);
      }
      .ticket__arrow {
        display: block;
        margin: 8px 0;
        font-size: 16px;
        width: 16px;
        height: 16px;
        color: var(--c-ink-faint);
      }
      .ticket__perforation {
        position: relative;
        height: 22px;
        background: repeating-linear-gradient(
            90deg,
            rgba(199, 165, 75, 0.5) 0 5px,
            transparent 5px 9px
          )
          center / calc(100% - 32px) 1px no-repeat;
      }
      .ticket__perforation::before,
      .ticket__perforation::after {
        content: '';
        position: absolute;
        top: 0;
        width: 22px;
        height: 22px;
        border-radius: 50%;
        background: var(--c-ivory);
        border: 1px solid var(--c-paper-edge);
        box-sizing: border-box;
      }
      .ticket__perforation::before {
        left: -11px;
      }
      .ticket__perforation::after {
        right: -11px;
      }
      .ticket__rows {
        padding: 12px 16px;
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .ticket__row {
        display: flex;
        align-items: center;
        gap: 12px;
        width: 100%;
      }
      .ticket__row--copy {
        border: 0;
        background: none;
        padding: 0;
        font: inherit;
        color: inherit;
        text-align: left;
        cursor: pointer;
      }
      .ticket__value {
        flex: 1 1 auto;
        min-width: 0;
        text-align: right;
        font-size: 14px;
        font-weight: 700;
        font-variant-numeric: tabular-nums slashed-zero;
        color: var(--c-ink);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .ticket__copy {
        font-size: 15px;
        width: 15px;
        height: 15px;
        color: var(--c-ink-faint);
      }
      .ticket__copy--ok {
        color: var(--c-positive);
      }
      .ticket__footnote {
        padding: 0 16px 12px;
      }
      .ticket__footnote:empty {
        display: none;
      }
    `,
  ],
})
export class TransferTicketComponent {
  readonly sendXof = input.required<string | number>();
  readonly receiveCny = input.required<string | number>();
  readonly receiveLabel = input('Le beneficiaire recoit');
  readonly receiveApprox = input(false);
  readonly rows = input<TicketRow[]>([]);

  readonly copied = signal<string | null>(null);

  async copy(row: TicketRow): Promise<void> {
    try {
      await navigator.clipboard.writeText(row.value);
      this.copied.set(row.label);
      setTimeout(() => this.copied.set(null), 1400);
    } catch {
      // Presse-papiers indisponible : la valeur reste lisible.
    }
  }
}
