import { Injectable, signal } from '@angular/core';

/** Evenement standard, non encore type par le DOM lib TypeScript. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const DISMISSED_STORAGE_KEY = 'pwa-install-dismissed';

/**
 * Installation du site comme application (mission "blocages Apple/Meta" oct. 2026 : le site
 * devient le canal de secours quand l'app native n'est pas installable). Android/Chrome/Edge
 * exposent `beforeinstallprompt` ; iOS Safari ne l'expose JAMAIS (aucune API : seul "Partager ->
 * Sur l'ecran d'accueil" existe), d'ou la distinction `canPromptInstall` / `isIosSafari`.
 */
@Injectable({ providedIn: 'root' })
export class PwaInstallService {
  private deferredPrompt: BeforeInstallPromptEvent | null = null;

  readonly canPromptInstall = signal(false);
  readonly isIosSafari = signal(false);
  readonly isStandalone = signal(false);
  readonly dismissed = signal(this.readDismissed());

  constructor() {
    // `matchMedia` est absent de l'environnement de test (jsdom minimal, voir app.spec.ts) --
    // jamais suppose present, meme discipline que localStorage plus bas.
    const standaloneDisplayMode =
      typeof window.matchMedia === 'function' && window.matchMedia('(display-mode: standalone)').matches;
    this.isStandalone.set(
      standaloneDisplayMode ||
        // Safari iOS : aucune media query standard, seule cette propriete non standard existe.
        (navigator as unknown as { standalone?: boolean }).standalone === true,
    );

    window.addEventListener('beforeinstallprompt', (event: Event) => {
      event.preventDefault();
      this.deferredPrompt = event as BeforeInstallPromptEvent;
      this.canPromptInstall.set(true);
    });

    window.addEventListener('appinstalled', () => {
      this.deferredPrompt = null;
      this.canPromptInstall.set(false);
      this.isStandalone.set(true);
    });

    const ua = window.navigator.userAgent;
    const isIos = /iphone|ipad|ipod/i.test(ua);
    const isSafari = /safari/i.test(ua) && !/crios|fxios|edgios/i.test(ua);
    this.isIosSafari.set(isIos && isSafari && !this.isStandalone());
  }

  /** Vrai des qu'il existe un moyen quelconque de proposer l'installation (bouton natif ou
   * instructions iOS), et que l'app n'est pas deja installee ni la bannière deja ecartee. */
  get shouldOfferInstall(): boolean {
    return !this.isStandalone() && !this.dismissed() && (this.canPromptInstall() || this.isIosSafari());
  }

  async promptInstall(): Promise<void> {
    const prompt = this.deferredPrompt;
    if (!prompt) return;
    await prompt.prompt();
    await prompt.userChoice;
    this.deferredPrompt = null;
    this.canPromptInstall.set(false);
  }

  dismiss(): void {
    this.dismissed.set(true);
    try {
      localStorage.setItem(DISMISSED_STORAGE_KEY, '1');
    } catch {
      // Stockage indisponible (navigation privee...) : la bannière reapparaitra a la prochaine
      // visite, jamais bloquant.
    }
  }

  private readDismissed(): boolean {
    try {
      return localStorage.getItem(DISMISSED_STORAGE_KEY) === '1';
    } catch {
      return false;
    }
  }
}
