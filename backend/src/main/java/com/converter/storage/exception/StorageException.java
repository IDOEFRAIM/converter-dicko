package com.converter.storage.exception;

/** Echec technique du stockage (disque plein, permissions, E/S). */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
