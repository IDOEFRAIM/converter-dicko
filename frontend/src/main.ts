import { isDevMode } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));

// PWA : enregistre le service worker (public/sw.js) en build de production
// uniquement — sous `ng serve`, un cache agressif masquerait les modifications.
// Un service worker exige HTTPS (ou localhost) : sur http:// le navigateur
// refuse l'enregistrement et ne proposera jamais d'installer l'application.
if (!isDevMode() && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch((err) =>
      console.warn('Service worker non enregistre', err),
    );
  });
}
