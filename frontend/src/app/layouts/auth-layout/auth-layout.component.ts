import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [RouterOutlet, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth-shell">
      <div class="auth-shell__brand">
        <span class="auth-shell__mark"><mat-icon>currency_exchange</mat-icon></span>
        Converter
      </div>
      <p class="auth-shell__tagline">Transferts XOF &rarr; CNY, simples et suivis</p>
      <div class="auth-shell__card">
        <router-outlet />
      </div>
    </div>
  `,
  styles: [
    `
      .auth-shell {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
        padding: 1.5rem;
        background:
          radial-gradient(circle at 15% -10%, rgba(26, 86, 219, 0.16), transparent 45%),
          radial-gradient(circle at 100% 10%, rgba(255, 176, 32, 0.12), transparent 40%),
          var(--mat-sys-surface-container-low, #f4f6fb);
      }
      .auth-shell__brand {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        font-size: 1.375rem;
        font-weight: 800;
        letter-spacing: -0.01em;
        color: #0a2a70;
      }
      .auth-shell__mark {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        border-radius: 12px;
        background: var(--brand-gradient);
        color: #fff;
        box-shadow: var(--shadow-sm);

        mat-icon {
          font-size: 22px;
          width: 22px;
          height: 22px;
        }
      }
      .auth-shell__tagline {
        margin: 0 0 1.5rem;
        font-size: 0.875rem;
        color: var(--mat-sys-on-surface-variant, #616161);
      }
      .auth-shell__card {
        width: 100%;
        max-width: 400px;
        background: var(--mat-sys-surface, #fff);
        border-radius: var(--radius-lg);
        padding: 2rem 1.75rem;
        box-shadow: var(--shadow-lg);
      }
    `,
  ],
})
export class AuthLayoutComponent {}
