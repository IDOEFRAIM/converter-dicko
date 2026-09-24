package com.converter.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Catalogue ferme des erreurs de l'API.
 *
 * <p>Chaque code porte son statut HTTP et sa categorie, ce qui garantit
 * qu'une meme situation metier produit toujours le meme statut, quel
 * que soit l'endpoint qui la leve.
 *
 * <p>Certains codes concernent des modules livres ulterieurement
 * (exchange, order, payment, treasury) : ils sont declares des
 * maintenant pour que le contrat d'erreur reste stable et que le
 * frontend puisse etre ecrit sans attendre.
 */
public enum ErrorCode {

    // ---------------------------------------------------------- 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, Category.VALIDATION_ERROR),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, Category.VALIDATION_ERROR),
    INVALID_PAYMENT_PROOF(HttpStatus.BAD_REQUEST, Category.BUSINESS_ERROR),
    ORDER_AMOUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, Category.BUSINESS_ERROR),
    INVALID_SETTING_VALUE(HttpStatus.BAD_REQUEST, Category.BUSINESS_ERROR),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, Category.BUSINESS_ERROR),

    // ---------------------------------------------------------- 401
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, Category.AUTHENTICATION_ERROR),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, Category.AUTHENTICATION_ERROR),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, Category.AUTHENTICATION_ERROR),

    // ---------------------------------------------------------- 403
    ACCESS_DENIED(HttpStatus.FORBIDDEN, Category.AUTHORIZATION_ERROR),
    USER_BLOCKED(HttpStatus.FORBIDDEN, Category.AUTHORIZATION_ERROR),

    // ---------------------------------------------------------- 404
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    PREFERRED_RATE_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    SUPPLIER_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    RATE_ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    BUSINESS_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),
    POOL_NOT_FOUND(HttpStatus.NOT_FOUND, Category.NOT_FOUND),

    // ---------------------------------------------------------- 409
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    INVALID_ORDER_STATE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    EXCHANGE_RATE_EXPIRED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    RATE_CHANGED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    INSUFFICIENT_TREASURY(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    DUPLICATE_TRANSACTION_REFERENCE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    PAYMENT_ALREADY_REVIEWED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    INVALID_QUOTE_STATE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    QUOTE_EXPIRED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    QUOTE_NOT_ACCEPTED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    QUOTE_ALREADY_USED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    INVALID_PAYMENT_STATE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    INVALID_SETTLEMENT_STATE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    TOO_MANY_OPEN_ORDERS(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    INSUFFICIENT_WALLET_BALANCE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    INVALID_PREFERRED_RATE_STATE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    IDEMPOTENT_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    REFUND_ALREADY_EXISTS(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    INVALID_REFUND_STATE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    SETTLEMENT_BLOCKED_BY_REFUND(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    SUPPLIER_INACTIVE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    SUPPLIER_QR_CODE_MISSING(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    RATE_ALERT_INACTIVE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    KYC_VERIFICATION_REQUIRED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    POOL_INACTIVE(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    POOL_ALREADY_JOINED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    POOL_NOT_JOINED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),
    ACCOUNT_DELETION_BLOCKED(HttpStatus.CONFLICT, Category.BUSINESS_ERROR),

    // ---------------------------------------------------------- 429
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, Category.RATE_LIMIT),

    // ---------------------------------------------------------- 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, Category.INTERNAL_ERROR),

    // ---------------------------------------------------------- 503
    EXCHANGE_RATE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Category.BUSINESS_ERROR),
    RATE_SOURCE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Category.BUSINESS_ERROR),
    COST_RATE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Category.BUSINESS_ERROR),
    GOOGLE_SIGNIN_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Category.BUSINESS_ERROR);

    private final HttpStatus status;
    private final String category;

    ErrorCode(HttpStatus status, String category) {
        this.status = status;
        this.category = category;
    }

    public HttpStatus status() {
        return status;
    }

    public String category() {
        return category;
    }

    /** Categories exposees dans le champ {@code error} de la reponse. */
    private static final class Category {
        static final String VALIDATION_ERROR = "VALIDATION_ERROR";
        static final String BUSINESS_ERROR = "BUSINESS_ERROR";
        static final String AUTHENTICATION_ERROR = "AUTHENTICATION_ERROR";
        static final String AUTHORIZATION_ERROR = "AUTHORIZATION_ERROR";
        static final String NOT_FOUND = "NOT_FOUND";
        static final String RATE_LIMIT = "RATE_LIMIT";
        static final String INTERNAL_ERROR = "INTERNAL_ERROR";

        private Category() {
        }
    }
}
