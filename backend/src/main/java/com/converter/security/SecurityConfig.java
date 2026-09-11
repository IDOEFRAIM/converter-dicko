package com.converter.security;

import com.converter.config.props.CorsProperties;
import com.converter.security.jwt.JwtAccessDeniedHandler;
import com.converter.security.jwt.JwtAuthenticationEntryPoint;
import com.converter.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Chaine de filtres de securite.
 *
 * <p>Regle d'or de ce fichier : {@code /api/admin/**} est reserve au
 * role {@code ADMIN} <b>ici, au niveau du filtre</b> — c'est la
 * premiere des deux barrieres annoncees en Phase 1 (la seconde est
 * {@code @PreAuthorize} sur chaque methode sensible des controleurs
 * admin, en Phase 8). Une regression sur l'annotation d'un seul
 * controleur ne suffit donc pas a ouvrir l'espace d'administration.
 *
 * <p>{@code SessionCreationPolicy.STATELESS} : aucune session HTTP.
 * Chaque requete s'authentifie par son jeton, ce qui permet de
 * deployer plusieurs instances du backend sans session partagee.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RequestIdFilter requestIdFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          RequestIdFilter requestIdFilter,
                          JwtAuthenticationEntryPoint authenticationEntryPoint,
                          JwtAccessDeniedHandler accessDeniedHandler,
                          CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.requestIdFilter = requestIdFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API sans etat consommee par un frontend distinct : aucun
                // formulaire HTML n'est soumis depuis le navigateur du meme
                // domaine, donc la protection CSRF (liee aux sessions cookie)
                // n'apporte rien ici et est desactivee au profit du CORS strict
                // ci-dessous et de l'authentification par jeton porte.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        // ---- Public ----
                        // Seuls l'inscription et la connexion sont publiques.
                        // /api/auth/me exige un jeton : ne PAS l'inclure ici
                        // sous un "/api/auth/**" generique, sous peine de le
                        // laisser franchir Spring Security sans principal —
                        // l'echec se produirait alors plus loin, dans
                        // CurrentUserArgumentResolver, sous la forme d'une
                        // erreur 500 au lieu d'un 401 propre.
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/google", "/api/auth/google/complete").permitAll()
                        .requestMatchers("/api/settings/public").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        // ---- Administration : role ADMIN uniquement ----
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // ---- Tout le reste exige une authentification ----
                        .anyRequest().authenticated())
                // requestIdFilter en premier : meme une requete rejetee par le rate limiter ou le
                // filtre JWT doit porter un identifiant de correlation dans ses journaux.
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Origines listees explicitement par profil : jamais de "*" ici,
        // incompatible avec allowCredentials(true) selon la specification
        // Fetch, et de toute facon trop permissif pour une API financiere.
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * BCrypt avec un cout de 12.
     *
     * <p>Le cout par defaut de Spring Security (10) reste raisonnable,
     * mais 12 est le palier communement recommande en 2026 pour un
     * hachage de mot de passe resistant au materiel actuel, sans
     * degrader la latence de connexion de facon perceptible.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
