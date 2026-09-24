package com.converter.storage.exception;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;

/** Fichier rejete : type MIME non autorise, taille excessive, contenu incoherent avec l'extension. */
public class InvalidFileException extends BusinessException {

    public InvalidFileException(String message) {
        super(ErrorCode.INVALID_PAYMENT_PROOF, message);
    }
}
