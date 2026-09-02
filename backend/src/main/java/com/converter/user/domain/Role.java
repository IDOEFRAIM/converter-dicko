package com.converter.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Role persiste, amorce par la migration V3.
 *
 * <p>La table est en lecture seule pour l'application : les roles sont
 * un referentiel ferme, pas une donnee administrable. Cela evite qu'un
 * defaut applicatif cree un role inattendu et contourne le controle
 * d'acces.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 20)
    private RoleCode code;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    protected Role() {
        // Requis par JPA.
    }

    public Short getId() {
        return id;
    }

    public RoleCode getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Role role && Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
