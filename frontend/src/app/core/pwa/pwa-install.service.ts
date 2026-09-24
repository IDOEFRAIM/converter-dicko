import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

/** Evenement non standard (Chrome/Edge/Samsung Internet) — absent des typings DOM. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

declare global {
  interface Window {
    /** Pose par le script inline de index.html, avant le demarrage d'Angular. */
    __yuanpayInstallPrompt?: BeforeInstallPromptEvent;
  }
}

/**
 * Comment installer le site sur cet appareil :
 *  - `prompt`  : le navigateur a fourni `beforeinstallprompt` -> vraie boite d'installation.
 *  - `ios`     : Safari iOS n'a AUCUNE API d'installation -> instructions "Partager > Sur l'ecran d'accueil".
 *  - `manual`  : autre navigateur mobile (Firefox...) -> instructions via le menu du navigateur.
 *  - `none`    : deja installe, ou bureau sans support -> rien a proposer.
 */
export type InstallMode = 'prompt' | 'ios' | 'manual' | 'none';

const DISMISS_KEY = 'yuanpay.install.dismissedAt';
/** Apres "Plus tard", on repropose au bout de 3 jours (pas a chaque connexion). */
const DISMISS_DURATION_MS = 3 * 24 * 60 * 60 * 1000;

/**
 * Proposition d'installation de la PWA ("telecharger le site" comme une app).
 *
 * Le bandeau s'affiche dans l'espace client, donc juste apres la connexion :
 * c'est le moment ou l'utilisateur a montre qu'il utilise reellement le service.
 */
@Injectable({ providedIn: 'root' })
export class PwaInstallService {
  private readonly document = inject(DOCUMENT);
  private readonly window = this.document.defaultView;

  private readonly deferredPrompt = signal<BeforeInstallPromptEvent | null>(
    this.window?.__yuanpayInstallPrompt ?? null,
  );
  private readonly installed = signal(this.detectStandalone());
  private readonly dismissed = signal(this.readDismissed());
  /** Forcer l'affichage (entree "Installer l'application" du menu Plus), meme apres "Plus tard". */
  private readonly forced = signal(false);

  readonly isIos = this.detectIos();
  private readonly isMobile = this.detectMobile();

  readonly mode = computed<InstallMode>(() => {
    if (this.installed()) {
      return 'none';
    }
    if (this.deferredPrompt()) {
      return 'prompt';
    }
    if (this.isIos) {
      return 'ios';
    }
    return this.isMobile ? 'manual' : 'none';
  });

  /** Vrai si l'application peut etre installee sur cet appareil (entree de menu). */
  readonly canInstall = computed(() => this.mode() !== 'none');

  /** Vrai si le bandeau doit etre visible maintenant. */
  readonly bannerVisible = computed(
    () => this.canInstall() && (this.forced() || !this.dismissed()),
  );

  constructor() {
    const win = this.window;
    if (!win) {
      return;
    }
    win.addEventListener('yuanpay-installable', () => {
      this.deferredPrompt.set(win.__yuanpayInstallPrompt ?? null);
    });
    // Au cas ou l'evenement arrive apres le demarrage d'Angular.
    win.addEventListener('beforeinstallprompt', (event) => {
      event.preventDefault();
      win.__yuanpayInstallPrompt = event as BeforeInstallPromptEvent;
      this.deferredPrompt.set(event as BeforeInstallPromptEvent);
    });
    win.addEventListener('appinstalled', () => {
      win.__yuanpayInstallPrompt = undefined;
      this.deferredPrompt.set(null);
      this.installed.set(true);
    });
    win.matchMedia?.('(display-mode: standalone)').addEventListener?.('change', (e) => {
      if (e.matches) {
        this.installed.set(true);
      }
    });
  }

  /** Ouvre la boite d'installation native. Renvoie vrai si l'utilisateur a accepte. */
  async install(): Promise<boolean> {
    const event = this.deferredPrompt();
    if (!event) {
      return false;
    }
    await event.prompt();
    const choice = await event.userChoice;
    // Un evenement ne peut servir qu'une fois.
    if (this.window) {
      this.window.__yuanpayInstallPrompt = undefined;
    }
    this.deferredPrompt.set(null);
    this.forced.set(false);
    if (choice.outcome === 'accepted') {
      this.installed.set(true);
      return true;
    }
    this.dismiss();
    return false;
  }

  /** "Plus tard" : masque le bandeau pour quelques jours. */
  dismiss(): void {
    this.forced.set(false);
    this.dismissed.set(true);
    try {
      this.window?.localStorage.setItem(DISMISS_KEY, String(Date.now()));
    } catch {
      // Stockage indisponible (navigation privee) : masque pour cette session seulement.
    }
  }

  /** Re-affiche le bandeau a la demande (menu Plus). */
  show(): void {
    this.forced.set(true);
  }

  private readDismissed(): boolean {
    try {
      const raw = this.window?.localStorage.getItem(DISMISS_KEY);
      return !!raw && Date.now() - Number(raw) < DISMISS_DURATION_MS;
    } catch {
      return false;
    }
  }

  private detectStandalone(): boolean {
    const win = this.window;
    if (!win) {
      return false;
    }
    const iosStandalone =
      (win.navigator as Navigator & { standalone?: boolean }).standalone === true;
    return iosStandalone || !!win.matchMedia?.('(display-mode: standalone)').matches;
  }

  private detectIos(): boolean {
    const nav = this.window?.navigator;
    if (!nav) {
      return false;
    }
    // iPadOS se presente comme un Mac : on le reconnait a l'ecran tactile.
    return (
      /iphone|ipad|ipod/i.test(nav.userAgent) ||
      (nav.platform === 'MacIntel' && nav.maxTouchPoints > 1)
    );
  }

  private detectMobile(): boolean {
    const nav = this.window?.navigator;
    return !!nav && /android|mobile/i.test(nav.userAgent);
  }
}
