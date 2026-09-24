package com.converter.security.jwt;

import com.converter.common.exception.ErrorCode;
import com.converter.security.CurrentUser;
import com.converter.security.SecurityResponseWriter;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authentifie chaque requete a partir de l'en-tete {@code Authorization}.
 *
 * <p>Point le plus important de ce filtre, conforme a la decision V5 :
 * le statut du compte est <b>relu en base a chaque requete</b>, pas
 * seulement extrait du jeton. Un jeton signe reste syntaxiquement
 * valide jusqu'a son expiration (2 heures) ; sans cette relecture, un
 * administrateur bloquant un utilisateur verrait sa decision retardee
 * jusqu'a l'expiration naturelle du jeton deja emis. Le cout — une
 * lecture d'index primaire par requete — est le prix assume de cette
 * garantie, explicitement accepte lors du choix "pas de refresh token
 * pour le MVP".
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityResponseWriter responseWriter;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository,
                                   SecurityResponseWriter responseWriter) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.responseWriter = responseWriter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            // Aucun jeton : on laisse passer. Un endpoint authentifie sans
            // principal en contexte sera rejete plus loin par
            // AuthorizationFilter via JwtAuthenticationEntryPoint.
            filterChain.doFilter(request, response);
            return;
        }

        JwtService.ParsedToken parsed;
        try {
            parsed = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Jeton invalide sur {} : {}", request.getRequestURI(), ex.getMessage());
            responseWriter.write(request, response, ErrorCode.INVALID_TOKEN,
                    "Jeton d'authentification invalide ou expire.");
            return;
        }

        Optional<User> maybeUser = userRepository.findById(parsed.userId());
        if (maybeUser.isEmpty()) {
            responseWriter.write(request, response, ErrorCode.INVALID_TOKEN,
                    "Ce compte n'existe plus.");
            return;
        }

        User user = maybeUser.get();
        if (!user.isActive()) {
            // Reponse deliberement distincte d'un jeton invalide : le client
            // peut afficher un message specifique ("compte desactive") plutot
            // que de proposer une reconnexion qui echouera de nouveau.
            responseWriter.write(request, response, ErrorCode.USER_BLOCKED,
                    "Ce compte a ete desactive. Contactez le support.");
            return;
        }

        authenticate(request, user);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Les chemins publics restent traites par doFilterInternal si un
        // jeton est present (pour peupler /api/auth/me le cas echeant),
        // mais ce filtre ne doit jamais BLOQUER une requete publique :
        // shouldNotFilter ne s'applique donc qu'aux methodes techniques
        // (preflight CORS) qui ne portent jamais de jeton.
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private void authenticate(HttpServletRequest request, User user) {
        CurrentUser principal = CurrentUser.fromEntity(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
