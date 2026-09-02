package com.converter.common.exception;

/** Violation d'une unicite metier (numero de telephone deja inscrit, etc.). */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static DuplicateResourceException phone(String phone) {
        return new DuplicateResourceException(ErrorCode.PHONE_ALREADY_REGISTERED,
                "Un compte existe deja avec le numero " + phone);
    }
}
