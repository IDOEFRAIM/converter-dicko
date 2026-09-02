package com.converter.user.domain;

/**
 * Roles applicatifs.
 *
 * <p>Les libelles correspondent a la colonne {@code roles.code}. Spring
 * Security attend le prefixe {@code ROLE_} dans les autorites : il est
 * ajoute a la construction du principal, jamais stocke en base.
 */
public enum RoleCode {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
