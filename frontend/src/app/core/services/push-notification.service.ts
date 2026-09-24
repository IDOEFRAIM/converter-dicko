import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { PushConfig, SubscribePushRequest, UnsubscribePushRequest } from '../models/push.model';

export type PushPermission = 'default' | 'granted' | 'denied' | 'unsupported';

/**
 * Notifications Web Push cote client (PWA, mission "blocages Apple/Meta" oct. 2026). Le
 * navigateur (pas ce service) est la seule source de verite sur l'abonnement effectif --
 * {@link refreshStatus} relit toujours `PushManager.getSubscription()` plutot que de faire
 * confiance a un etat local qui pourrait diverger (abonnement revoque par l'utilisateur dans les
 * reglages du navigateur, par exemple).
 */
@Injectable({ providedIn: 'root' })
export class PushNotificationService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/push`;

  readonly supported = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  readonly permission = signal<PushPermission>(this.supported ? (Notification.permission as PushPermission) : 'unsupported');
  readonly subscribed = signal(false);
  readonly busy = signal(false);
  readonly errorMessage = signal<string | null>(null);

  constructor(private readonly http: HttpClient) {}

  config(): Observable<ApiResponse<PushConfig>> {
    return this.http.get<ApiResponse<PushConfig>>(`${this.baseUrl}/config`);
  }

  /** A appeler au chargement d'une page qui affiche l'etat de l'abonnement. */
  async refreshStatus(): Promise<void> {
    if (!this.supported) return;
    this.permission.set(Notification.permission as PushPermission);
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      this.subscribed.set(subscription !== null);
    } catch {
      this.subscribed.set(false);
    }
  }

  async subscribe(vapidPublicKey: string): Promise<boolean> {
    if (!this.supported || this.busy()) return false;
    this.busy.set(true);
    this.errorMessage.set(null);
    try {
      const permission = await Notification.requestPermission();
      this.permission.set(permission as PushPermission);
      if (permission !== 'granted') {
        return false;
      }
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
      });
      const json = subscription.toJSON();
      const request: SubscribePushRequest = {
        endpoint: json.endpoint!,
        keys: { p256dh: json.keys!['p256dh'], auth: json.keys!['auth'] },
      };
      await this.postSubscription(request);
      this.subscribed.set(true);
      return true;
    } catch {
      this.errorMessage.set("Impossible d'activer les notifications. Reessayez.");
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  async unsubscribe(): Promise<void> {
    if (!this.supported || this.busy()) return;
    this.busy.set(true);
    this.errorMessage.set(null);
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      if (subscription) {
        const endpoint = subscription.endpoint;
        await subscription.unsubscribe();
        await this.deleteSubscription({ endpoint });
      }
      this.subscribed.set(false);
    } catch {
      this.errorMessage.set('Impossible de desactiver les notifications. Reessayez.');
    } finally {
      this.busy.set(false);
    }
  }

  private postSubscription(request: SubscribePushRequest): Promise<unknown> {
    return new Promise((resolve, reject) => {
      this.http.post<ApiResponse<void>>(`${this.baseUrl}/subscriptions`, request).subscribe({
        next: resolve,
        error: reject,
      });
    });
  }

  private deleteSubscription(request: UnsubscribePushRequest): Promise<unknown> {
    return new Promise((resolve, reject) => {
      this.http.request<ApiResponse<void>>('DELETE', `${this.baseUrl}/subscriptions`, { body: request }).subscribe({
        next: resolve,
        error: reject,
      });
    });
  }
}

/** Conversion standard base64url -> Uint8Array, requise par `PushManager.subscribe()`
 * (`applicationServerKey`) -- la cle VAPID est transmise en base64url, jamais en binaire brut. */
function urlBase64ToUint8Array(base64String: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = atob(base64);
  // Buffer alloue explicitement en `ArrayBuffer` (pas `ArrayBufferLike`) : requis par le type
  // `BufferSource` attendu par `applicationServerKey`.
  const outputArray = new Uint8Array(new ArrayBuffer(rawData.length));
  for (let i = 0; i < rawData.length; i++) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}
