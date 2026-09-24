package com.converter.common.exception;

/** Trop de tentatives de connexion echouees pour ce compte ou cette IP. */
public class TooManyAttemptsException extends BusinessException {

    public TooManyAttemptsException(int lockMinutes) {
        super(ErrorCode.TOO_MANY_ATTEMPTS,
                "Trop de tentatives de connexion. Reessayez dans "
                        + lockMinutes + " minutes.");
    }
}
