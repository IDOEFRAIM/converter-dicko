package com.converter.security;

import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Principal Spring Security place dans le {@code SecurityContext}.
 *
 * <p>Volontairement leger : il ne porte que l'identifiant, le numero et
 * les roles necessaires au controle d'acces. Le mot de passe (condense)
 * n'y figure pas — {@link JwtAuthenticationFilter} authentifie par
 * jeton, jamais par mot de passe, donc {@link #getPassword()} n'a
 * aucun usage reel et renvoie {@code null}.
 */
public final class CurrentUser implements UserDetails {

    private final UUID id;
    private final String phone;
    private final Set<RoleCode> roles;
    private final boolean enabled;

    public CurrentUser(UUID id, String phone, Set<RoleCode> roles, boolean enabled) {
        this.id = id;
        this.phone = phone;
        this.roles = roles;
        this.enabled = enabled;
    }

    public static CurrentUser fromEntity(User user) {
        Set<RoleCode> roles = user.getRoles().stream()
                .map(role -> role.getCode())
                .collect(Collectors.toSet());
        return new CurrentUser(user.getId(), user.getPhone(), roles, user.isActive());
    }

    public UUID getId() {
        return id;
    }

    public boolean isAdmin() {
        return roles.contains(RoleCode.ADMIN);
    }

    @Override
    public Set<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return phone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Le blocage administratif est un domaine distinct de "verrouille" :
        // il est verifie explicitement dans JwtAuthenticationFilter, avant
        // meme la construction de ce principal, afin de renvoyer un code
        // d'erreur metier (USER_BLOCKED) plutot que l'erreur generique de
        // Spring Security.
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
