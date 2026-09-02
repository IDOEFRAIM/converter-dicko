package com.converter.common.exception;

/**
 * Erreur metier attendue : une regle du domaine refuse l'operation.
 *
 * <p>Distincte d'un defaut technique. Elle porte un {@link ErrorCode}
 * qui determine a la fois le statut HTTP et le code stable renvoye au
 * client, ce qui evite d'eparpiller des {@code ResponseStatusException}
 * dans les services.
 *
 * <p>N'herite pas de la stack trace : ces exceptions sont frequentes et
 * previsibles, la remplir couterait sans rien apporter au diagnostic.
 */
public class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message, null, false, false);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
