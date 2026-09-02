package com.converter.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.util.Objects;
import java.util.UUID;

/**
 * Racine des entites identifiees par UUID.
 *
 * <p>L'UUID est prefere a une sequence pour tout identifiant expose :
 * il n'est pas enumerable, donc un client ne peut pas deviner l'ordre
 * ou le nombre de ressources qui ne lui appartiennent pas.
 *
 * <p>{@code equals}/{@code hashCode} reposent sur l'identifiant et non
 * sur les champs metier. Le {@code hashCode} est constant afin de
 * rester correct avant et apres persistance, ce qui est indispensable
 * lorsqu'une entite transite par un {@code HashSet}.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        // Deux entites non encore persistees ne sont egales que si elles
        // sont la meme instance, cas deja traite ci-dessus.
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
