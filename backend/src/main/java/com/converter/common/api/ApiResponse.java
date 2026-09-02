package com.converter.common.api;

/**
 * Enveloppe unique de toutes les reponses de succes de l'API.
 *
 * <pre>
 * { "data": { ... }, "message": "Operation successful" }
 * </pre>
 *
 * <p>Uniformiser la forme permet au frontend d'ecrire un seul
 * deballage, et evite qu'un endpoint renvoie un tableau nu — un cas
 * historiquement exploitable par detournement JSON.
 */
public record ApiResponse<T>(T data, String message) {

    private static final String DEFAULT_MESSAGE = "Operation successful";

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, DEFAULT_MESSAGE);
    }

    public static <T> ApiResponse<T> of(T data, String message) {
        return new ApiResponse<>(data, message);
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(null, message);
    }
}
