import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// Le service worker (PWA + notifications push) est enregistre par
// provideServiceWorker dans app.config.ts.
bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
