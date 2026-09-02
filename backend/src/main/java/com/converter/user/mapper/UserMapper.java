package com.converter.user.mapper;

import com.converter.user.domain.Role;
import com.converter.user.domain.User;
import com.converter.user.dto.UserResponse;
import org.mapstruct.Mapper;

/**
 * Conversion entite vers DTO de sortie.
 *
 * <p>Le sens inverse n'existe pas volontairement : une entite ne doit
 * jamais etre construite par recopie automatique d'une entree client,
 * sous peine d'affecter un champ que le client n'a pas le droit de
 * fixer (statut, roles, identifiant).
 */
@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);

    default String roleToCode(Role role) {
        return role.getCode().name();
    }
}
