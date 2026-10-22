
package com.aibuilder.config;

import com.aibuilder.auth.security.JwtAuthenticationFilter;
import com.aibuilder.fullstack.runtime.PreviewSessionAuthenticationFilter;
import com.aibuilder.fullstack.runtime.PreviewSessionOriginFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PreviewSessionAuthenticationFilter previewSessionAuthenticationFilter;
    private final PreviewSessionOriginFilter previewSessionOriginFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .headers(headers ->
                        headers.frameOptions(frame -> frame.disable())
                )

                .authorizeHttpRequests(auth -> auth

                        // Generated application API.
                        // Authentication must be established by the
                        // preview-session filter.
                        .requestMatchers(
                                "/api/projects/*/runtime/api/**"
                        ).authenticated()

                        .requestMatchers(
                                "/api/internal/fullstack/**"
                        ).authenticated()

                        .requestMatchers(
                                "/api/auth/**",
                                "/api/health",
                                "/actuator/health",
                                "/api/projects/*/previews/**",
                                "/error"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .addFilterAfter(
                        previewSessionAuthenticationFilter,
                        JwtAuthenticationFilter.class
                )

                .addFilterAfter(
                        previewSessionOriginFilter,
                        PreviewSessionAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<PreviewSessionAuthenticationFilter>
    disablePreviewFilterAutoRegistration(
            PreviewSessionAuthenticationFilter filter
    ) {
        FilterRegistrationBean<PreviewSessionAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PreviewSessionOriginFilter>
    disablePreviewOriginFilterAutoRegistration(
            PreviewSessionOriginFilter filter
    ) {
        FilterRegistrationBean<PreviewSessionOriginFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter>
    disableJwtFilterAutoRegistration(
            JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }
}
