/*
 * Service worker de la PWA YUAN PAY BF (espace client).
 *
 * Pourquoi un service worker ecrit a la main plutot que @angular/service-worker :
 * aucune dependance supplementaire, et une strategie volontairement simple et
 * previsible pour une app de paiement :
 *   - /api/*            : JAMAIS mis en cache (donnees financieres toujours fraiches).
 *   - navigations (HTML) : reseau d'abord, repli sur l'index.html en cache hors ligne.
 *   - fichiers statiques : cache d'abord (les bundles Angular sont hashes -> immuables).
 *
 * La presence d'un gestionnaire `fetch` est aussi l'un des criteres
 * d'installabilite de Chrome/Edge : sans lui, `beforeinstallprompt` n'est
 * jamais emis et le navigateur ne propose pas d'installer le site.
 *
 * Changer CACHE_VERSION invalide tous les caches existants au prochain deploiement.
 */
const CACHE_VERSION = 'v1';
const SHELL_CACHE = `yuanpay-shell-${CACHE_VERSION}`;
const ASSET_CACHE = `yuanpay-assets-${CACHE_VERSION}`;

const SHELL_URLS = [
  '/',
  '/index.html',
  '/manifest.webmanifest',
  '/logo.png',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll(SHELL_URLS))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith('yuanpay-') && key !== SHELL_CACHE && key !== ASSET_CACHE)
            .map((key) => caches.delete(key)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') {
    return;
  }

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return;
  }
  if (url.pathname.startsWith('/api/') || url.pathname === '/healthz' || url.pathname === '/sw.js') {
    return;
  }

  if (request.mode === 'navigate') {
    event.respondWith(networkFirstNavigation(request));
    return;
  }

  if (/\.(?:js|mjs|css|woff2?|ttf|otf|png|jpe?g|gif|svg|ico|webp)$/.test(url.pathname)) {
    event.respondWith(cacheFirst(request));
  }
});

async function networkFirstNavigation(request) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(SHELL_CACHE);
      cache.put('/index.html', response.clone());
    }
    return response;
  } catch (error) {
    const cached = (await caches.match('/index.html')) || (await caches.match('/'));
    if (cached) {
      return cached;
    }
    throw error;
  }
}

async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) {
    return cached;
  }
  const response = await fetch(request);
  if (response.ok && response.type === 'basic') {
    const cache = await caches.open(ASSET_CACHE);
    cache.put(request, response.clone());
  }
  return response;
}
