package com.am.market_hub.auth.security;

import java.io.IOException;
import java.util.List;

import com.am.market_hub.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads a bearer token and, if valid, authenticates the request. Wired into
 * {@link SecurityConfig}'s filter chain — not registered as a bare
 * {@code @Component} the way {@code HttpRequestLoggingFilter} is, since its
 * ordering relative to Spring Security's own chain would then be undefined.
 *
 * <p>On any parse failure (missing header, expired, tampered, malformed), on
 * a claimed user id that no longer exists, or on a user that is now
 * {@code blocked}, this filter does nothing but continue the chain — it
 * never throws and never writes a response. Whether that matters is decided
 * downstream, by whatever the request actually needed: a public endpoint
 * proceeds unauthenticated, a protected one hits
 * {@link JwtAuthenticationEntryPoint}. The {@code blocked} lookup is the one
 * per-request DB check this otherwise fully stateless filter makes — it's
 * what lets an administrative block revoke a session immediately instead of
 * only once the token expires (constraints.md).
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            try {
                Claims claims = jwtService.parse(token);
                Long userId = claims.get("userId", Long.class);
                userRepository.findById(userId)
                        .filter(user -> !user.isBlocked())
                        .ifPresent(user -> {
                            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, claims.getSubject());
                            String role = claims.get("role", String.class);
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            } catch (JwtException ignored) {
                // Leave the request unauthenticated; downstream access control decides the outcome.
            }
        }
        filterChain.doFilter(request, response);
    }
}
