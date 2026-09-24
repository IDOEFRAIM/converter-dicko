import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';

/**
 * Corridor "hero" 🇧🇫 -> 🇨🇳 de l'accueil — traduction web de `Corridor(level: hero)` +
 * `CorridorFlow` (mobile) : degrade de l'habillage, filet d'or, et le fil courbe le
 * long duquel circulent trois "motes" de lumiere. Purement decoratif. Les animations
 * se figent si l'utilisateur a demande la reduction des mouvements.
 */
@Component({
  selector: 'app-corridor-hero',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hero">
      <div class="hero__ends">
        <span class="hero__end"
          ><span class="hero__flag" aria-hidden="true">🇧🇫</span>{{ leftLabel() }}</span
        >
        <span class="hero__end"
          >{{ rightLabel() }}<span class="hero__flag" aria-hidden="true">🇨🇳</span></span
        >
      </div>
      <svg class="hero__flow" [attr.viewBox]="'0 0 ' + width() + ' 56'" aria-hidden="true">
        <path class="hero__rail" [attr.d]="path()" />
        <circle class="hero__start" cx="4" [attr.cy]="startY" r="4" />
        <circle class="hero__stop" [attr.cx]="width() - 4" [attr.cy]="endY" r="4.5" />
        @for (delay of motes; track delay) {
          <circle class="hero__mote" r="3">
            <animateMotion
              dur="5.2s"
              repeatCount="indefinite"
              [attr.begin]="delay + 's'"
              [attr.path]="path()"
            />
          </circle>
        }
      </svg>
    </div>
  `,
  styles: [
    `
      .hero {
        position: relative;
        overflow: hidden;
        padding: 16px 16px 12px;
        border-radius: var(--radius-md);
        border: 1px solid rgba(199, 165, 75, 0.45);
        background: var(--accent-gradient);
        color: var(--accent-gradient-fg);
      }
      /* LedgerGrain : lignes de registre tres discretes. */
      .hero::before {
        content: '';
        position: absolute;
        inset: 0;
        opacity: 0.06;
        background: repeating-linear-gradient(0deg, currentColor 0 1px, transparent 1px 7px);
        pointer-events: none;
      }
      .hero__ends {
        position: relative;
        display: flex;
        justify-content: space-between;
        gap: 8px;
      }
      .hero__end {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        font-size: 14px;
        font-weight: 800;
        letter-spacing: -0.2px;
        white-space: nowrap;
      }
      .hero__flag {
        font-size: 22px;
        line-height: 1;
      }
      .hero__flow {
        position: relative;
        display: block;
        width: 100%;
        height: 56px;
        overflow: visible;
      }
      .hero__rail {
        fill: none;
        stroke: currentColor;
        stroke-opacity: 0.22;
        stroke-width: 1.5;
        stroke-linecap: round;
        vector-effect: non-scaling-stroke;
      }
      .hero__start {
        fill: none;
        stroke: currentColor;
        stroke-width: 1.5;
        vector-effect: non-scaling-stroke;
      }
      .hero__stop {
        fill: currentColor;
      }
      .hero__mote {
        fill: var(--c-signal);
        filter: drop-shadow(0 0 4px rgba(47, 184, 166, 0.8));
      }
      @media (prefers-reduced-motion: reduce) {
        .hero__mote {
          display: none;
        }
      }
    `,
  ],
})
export class CorridorHeroComponent {
  readonly leftLabel = input('Burkina Faso');
  readonly rightLabel = input('Chine');
  /** Decalage (s) de chaque mote le long du fil — 3 motes, comme `CorridorFlow(motes: 3)`. */
  readonly motes = [0, -1.73, -3.46];

  /** Meme courbe que `_CorridorFlowPainter` : cubique, amplitude 22 % de la hauteur. */
  private static readonly HEIGHT = 56;
  private static readonly AMP = CorridorHeroComponent.HEIGHT * 0.22;
  readonly startY = CorridorHeroComponent.HEIGHT / 2 + CorridorHeroComponent.AMP * 0.15;
  readonly endY = CorridorHeroComponent.HEIGHT / 2 - CorridorHeroComponent.AMP * 0.15;

  /** Largeur reelle en px : le trace n'est jamais etire (les motes restent rondes). */
  readonly width = signal(320);
  readonly path = computed(() => {
    const w = this.width();
    const mid = CorridorHeroComponent.HEIGHT / 2;
    const amp = CorridorHeroComponent.AMP;
    return `M4,${this.startY} C${w * 0.3},${mid + amp} ${w * 0.7},${mid - amp} ${w - 4},${this.endY}`;
  });

  constructor() {
    const host = inject(ElementRef<HTMLElement>);
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const svg = (host.nativeElement as HTMLElement).querySelector('svg');
      if (!svg || typeof ResizeObserver === 'undefined') {
        return;
      }
      const observer = new ResizeObserver(([entry]) => {
        const w = Math.round(entry.contentRect.width);
        if (w > 0) {
          this.width.set(w);
        }
      });
      observer.observe(svg);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }
}
