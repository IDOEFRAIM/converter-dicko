package com.converter.common.exception;

/**
 * Ressource inexistante — ou volontairement presentee comme telle.
 *
 * <p>Un acces a une ressource appartenant a autrui doit lever cette
 * exception plutot qu'un refus d'autorisation : un {@code 403}
 * confirmerait l'existence de la ressource et permettrait de
 * l'enumerer.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ResourceNotFoundException user(Object id) {
        return new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND,
                "Utilisateur introuvable : " + id);
    }

    public static ResourceNotFoundException setting(Object key) {
        return new ResourceNotFoundException(ErrorCode.SETTING_NOT_FOUND,
                "Parametre introuvable : " + key);
    }
}
