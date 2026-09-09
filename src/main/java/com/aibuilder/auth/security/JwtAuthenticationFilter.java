package com.aibuilder.auth.security;

import com.aibuilder.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {

            if (!jwtService.isTokenValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            Claims claims = jwtService.extractAllClaims(token);

            String email = claims.getSubject();

            if (SecurityContextHolder
                    .getContext()
                    .getAuthentication() == null) {

                userRepository.findByEmail(email.toLowerCase())
                        .ifPresent(user -> {

                            if (Boolean.TRUE.equals(user.getEnabled())) {

                                var authorities = List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_" +
                                                        user.getRole().name()
                                        )
                                );

                                var authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                user.getEmail(),
                                                null,
                                                authorities
                                        );

                                SecurityContextHolder
                                        .getContext()
                                        .setAuthentication(authentication);
                            }
                        });
            }

        } catch (Exception ignored) {

        }

        filterChain.doFilter(request, response);
    }
}