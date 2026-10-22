package com.aibuilder.auth.security;

import com.aibuilder.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getRequestURI();

        // Generated app API uses preview-session authentication,
        // not the AI Builder user's JWT.
        return path.matches(
                "^/api/projects/\\d+/runtime/api(?:/.*)?$"
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        log.warn(
                "[JWT DEBUG] method={}, path={}, authorizationPresent={}, bearer={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader("Authorization") != null,
                request.getHeader("Authorization") != null
                        && request.getHeader("Authorization").startsWith("Bearer ")
        );

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug(
                    "JWT authentication: Bearer header missing for {}",
                    request.getRequestURI()
            );

            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {

            if (!jwtService.isTokenValid(token)) {

                log.warn(
                        "JWT authentication: Invalid token for {}",
                        request.getRequestURI()
                );

                SecurityContextHolder.clearContext();

                filterChain.doFilter(request, response);
                return;
            }

            Claims claims = jwtService.extractAllClaims(token);

            String email = claims.getSubject();

            if (email == null || email.isBlank()) {

                log.warn("JWT authentication: Missing subject");

                SecurityContextHolder.clearContext();

                filterChain.doFilter(request, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                var userOptional = userRepository.findByEmail(
                        email.toLowerCase(Locale.ROOT)
                );

                if (userOptional.isEmpty()) {

                    log.warn(
                            "JWT authentication: Token subject has no matching user"
                    );

                } else {

                    var user = userOptional.get();

                    if (!Boolean.TRUE.equals(user.getEnabled())) {

                        log.warn(
                                "JWT authentication: User account disabled"
                        );

                    } else {

                        String role = user.getRole().name();

                        String authority = role.startsWith("ROLE_")
                                ? role
                                : "ROLE_" + role;

                        var authorities = List.of(
                                new SimpleGrantedAuthority(authority)
                        );

                        var authentication =
                                new UsernamePasswordAuthenticationToken(
                                        user.getEmail(),
                                        null,
                                        authorities
                                );

                        SecurityContextHolder.getContext()
                                .setAuthentication(authentication);

                        log.info(
                                "JWT authentication successful for {}",
                                request.getRequestURI()
                        );
                    }
                }
            }

        } catch (Exception ex) {

            SecurityContextHolder.clearContext();

            log.warn(
                    "JWT authentication failed for {}: {}",
                    request.getRequestURI(),
                    ex.getClass().getSimpleName()
            );

            log.debug("JWT authentication exception", ex);
        }

        filterChain.doFilter(request, response);
    }
}