// Service worker combine : la mise en cache/mise a jour de l'app reste entierement geree par
// Angular (ngsw-worker.js, importe ci-dessous -- voir ngsw-config.json pour la strategie, qui
// exclut deliberement toute reponse /api/**). Ce fichier ajoute UNIQUEMENT la reception des
// notifications Web Push et le clic dessus (PWA, mission "blocages Apple/Meta" oct. 2026) :
// Angular n'a aucun support integre pour push/notificationclick, d'ou ce fichier plutot que
// d'enregistrer ngsw-worker.js directement (voir provideServiceWorker dans app.config.ts, qui
// enregistre CE fichier, pas ngsw-worker.js).
importScripts('/ngsw-worker.js');

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    // Payload non-JSON (improbable, le backend envoie toujours {title, body}) : notification
    // generique plutot qu'un evenement muet.
  }
  const title = data.title || 'YUAN PAY BF';
  const options = {
    body: data.body || '',
    icon: '/icons/icon-192x192.png',
    badge: '/icons/icon-96x96.png',
    data: { url: data.url || '/notifications' },
  };
  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || '/notifications';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      for (const client of windowClients) {
        if (client.url.includes(url) && 'focus' in client) {
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(url);
      }
    }),
  );
});
