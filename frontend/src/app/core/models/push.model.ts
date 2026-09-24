/** Miroir de `PushConfigResponse` backend — `vapidPublicKey` n'est jamais un secret (RFC 8292). */
export interface PushConfig {
  available: boolean;
  vapidPublicKey: string | null;
}

/** Miroir exact de l'objet standard renvoye par `PushManager.subscribe()` cote navigateur. */
export interface SubscribePushRequest {
  endpoint: string;
  keys: {
    p256dh: string;
    auth: string;
  };
}

export interface UnsubscribePushRequest {
  endpoint: string;
}
