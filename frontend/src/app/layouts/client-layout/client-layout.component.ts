import { Location } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  DestroyRef,
  OnInit,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { filter, map } from 'rxjs';
import { ExperienceProfile } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { InboxNotificationService } from '../../core/services/inbox-notification.service';
import { InstallBannerComponent } from '../../shared/components/install-banner/install-banner.component';

const UNREAD_POLL_INTERVAL_MS = 30_000;

export type ShellTab = 'home' | 'pay' | 'suppliers' | 'activity' | 'support' | 'more';

interface ShellDestination {
  tab: ShellTab;
  link: string;
  icon: string;
  label: string;
}

interface AppBarAction {
  icon: string;
  link: string;
  label: string;
}

interface ScreenData {
  tab: ShellTab;
  title: string;
  root: boolean;
  hideAppBar: boolean;
  action: AppBarAction | null;
}

/**
 * Les 6 onglets de l'application mobile, dans le meme ordre, avec les memes icones
 * (`mobile/lib/app/router/app_shell.dart`). Icone "contour" au repos, "pleine" quand
 * l'onglet est actif ; seul l'onglet actif affiche son libelle (onlyShowSelected).
 */
const DESTINATIONS: readonly ShellDestination[] = [
  { tab: 'home', link: '/home', icon: 'home', label: 'Accueil' },
  { tab: 'pay', link: '/pay', icon: 'send', label: 'Payer' },
  { tab: 'suppliers', link: '/suppliers', icon: 'storefront', label: 'Fournisseurs' },
  { tab: 'activity', link: '/activity', icon: 'receipt_long', label: 'Activite' },
  { tab: 'support', link: '/support', icon: 'forum', label: 'Messages' },
  { tab: 'more', link: '/more', icon: 'more_horiz', label: 'Plus' },
];

const PROFILE_CLASSES: Record<ExperienceProfile, string> = {
  PRO: 'profile-pro',
  STUDENT_MALE: 'profile-student-male',
  STUDENT_FEMALE: 'profile-student-female',
};

/**
 * Coquille de l'espace client — copie conforme de `AppShell` (Flutter) : barre
 * d'application (titre + cloche) en haut, barre de navigation a 6 onglets en bas,
 * toujours visible. Sur grand ecran, l'application reste une colonne de telephone
 * centree : le site est une alternative a l'app, pas un second produit.
 */
@Component({
  selector: 'app-client-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, MatButtonModule, MatIconModule, MatBadgeModule, InstallBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './client-layout.component.html',
  styleUrl: './client-layout.component.scss',
})
export class ClientLayoutComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly document = inject(DOCUMENT);
  private readonly notificationService = inject(InboxNotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly destinations = DESTINATIONS;
  readonly unreadCount = signal(0);

  /** Nombre de navigations internes : si 0, "retour" doit remonter a la racine de l'onglet. */
  private navigationCount = 0;

  private readonly screen = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.readScreenData()),
    ),
    { initialValue: this.readScreenData() },
  );

  readonly activeTab = computed(() => this.screen().tab);
  readonly title = computed(() => this.screen().title);
  readonly isRoot = computed(() => this.screen().root);
  readonly hideAppBar = computed(() => this.screen().hideAppBar);
  readonly action = computed(() => this.screen().action);

  constructor() {
    // Habillage du profil (ExperiencePalette) : pose sur <body> pour que les
    // dialogues/menus Material (rendus hors de ce composant) suivent aussi.
    effect((onCleanup) => {
      const cls = PROFILE_CLASSES[this.auth.experienceProfile?.() ?? 'PRO'] ?? PROFILE_CLASSES.PRO;
      const body = this.document.body;
      body.classList.add(cls);
      onCleanup(() => body.classList.remove(cls));
    });

    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.navigationCount++;
        // Comme un changement d'ecran mobile : on repart du haut de page.
        this.document.defaultView?.scrollTo?.({ top: 0 });
      });
  }

  ngOnInit(): void {
    this.refreshUnreadCount();
    const intervalId = setInterval(() => this.refreshUnreadCount(), UNREAD_POLL_INTERVAL_MS);
    this.destroyRef.onDestroy(() => clearInterval(intervalId));
  }

  back(): void {
    if (this.navigationCount > 1) {
      this.location.back();
      return;
    }
    const tab = DESTINATIONS.find((d) => d.tab === this.activeTab());
    this.router.navigateByUrl(tab?.link ?? '/home');
  }

  private readScreenData(): ScreenData {
    let snapshot: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    let data: Record<string, unknown> = {};
    while (snapshot) {
      data = { ...data, ...snapshot.data };
      snapshot = snapshot.firstChild;
    }
    return {
      tab: (data['tab'] as ShellTab) ?? 'home',
      title: (data['title'] as string) ?? '',
      root: data['root'] === true,
      hideAppBar: data['hideAppBar'] === true,
      action: (data['action'] as AppBarAction) ?? null,
    };
  }

  private refreshUnreadCount(): void {
    this.notificationService.unreadCount().subscribe({
      next: (response) => this.unreadCount.set(response.data.unreadCount),
      error: () => undefined,
    });
  }
}
