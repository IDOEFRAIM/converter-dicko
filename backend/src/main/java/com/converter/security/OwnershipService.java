package com.converter.security;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Verifie qu'un client n'accede qu'a ses propres ressources.
 *
 * <p>Utilise par les modules livres ulterieurement (order, payment) au
 * moment ou leurs repositories renverraient une ressource sans avoir
 * pu filtrer par proprietaire au niveau SQL (cas rare : le filtrage en
 * base reste la premiere ligne de defense partout ou c'est possible).
 *
 * <p>Renvoie systematiquement {@link ErrorCode#ORDER_NOT_FOUND}-like,
 * c'est-a-dire un <b>404</b>, jamais un 403 : un 403 confirmerait
 * l'existence de la ressource a un utilisateur qui n'a pas a le savoir,
 * ouvrant une enumeration des identifiants d'autrui.
 */
@Component
public class OwnershipService {

    public void assertOwnedBy(UUID resourceOwnerId, UUID currentUserId, ErrorCode notFoundCode, String message) {
        if (!resourceOwnerId.equals(currentUserId)) {
            throw new BusinessException(notFoundCode, message);
        }
    }
}
