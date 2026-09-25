package com.converter.transfi.client;

/** Echec d'appel a l'API TransFi (reseau, timeout, 4xx/5xx) — jamais avale en silence par l'appelant. */
public class TransFiApiException extends RuntimeException {

    public TransFiApiException(String message) {
        super(message);
    }

    public TransFiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
