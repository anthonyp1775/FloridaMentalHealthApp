package com.flmentalhealth.config;

import com.flmentalhealth.security.JwtAuthEntryPoint;
import com.flmentalhealth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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
 * Stateless JWT security.
 *
 * This is deliberately NOT the session-based, form-login pattern used
 * in the 309 labs. A React single-page app served from a different
 * origin needs a token it can attach to each request, not a session
 * cookie tied to a server-rendered page. See ADR 0001.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize("hasRole('ADMIN')") on methods
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JwtAuthEntryPoint authEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          JwtAuthEntryPoint authEntryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authEntryPoint = authEntryPoint;
    }

    /**
     * BCrypt at strength 11 - 2^11 = 2,048 rounds.
     *
     * Defined once as a bean so registration and login always use
     * identical settings. Hardcoding a different strength in two places
     * is a classic way to produce hashes that never verify.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(11);
    }

    /**
     * Exposes the AuthenticationManager that Spring Boot auto-configures
     * from the CustomUserDetailsService and PasswordEncoder beans, so
     * AuthService can call it to verify a login.
     *
     * Taking it from AuthenticationConfiguration rather than building a
     * DaoAuthenticationProvider by hand keeps this stable across Spring
     * Security versions - that constructor has changed more than once.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            /*
             * CSRF protects against a browser silently attaching a
             * session cookie to a forged request. There is no session
             * and no auth cookie here - the token is attached by our own
             * JavaScript - so there is nothing for CSRF to protect, and
             * leaving it on would simply break every POST.
             */
            .csrf(csrf -> csrf.disable())

            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            /*
             * STATELESS: never create an HttpSession, never consult one.
             * Every request must carry its own token. This is what makes
             * the API horizontally scalable (NFR-19) - any instance can
             * serve any request with no shared session store.
             */
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                    // Public: you cannot have a token before you log in.
                    .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()

                    // API documentation.
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                     "/v3/api-docs", "/v3/api-docs/**").permitAll()

                    // CORS preflight - the browser sends these without credentials.
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // Reports are staff-only. Declared here as well as on
                    // the methods, so a forgotten annotation cannot open
                    // them up by accident.
                    .requestMatchers("/api/reports/**").hasRole("ADMIN")
                    .requestMatchers("/api/referrals/queue").hasRole("ADMIN")

                    // Everything else needs a valid token. Per-endpoint
                    // role rules live on the methods via @PreAuthorize.
                    .anyRequest().authenticated()
            )

            /*
             * Without this, Spring Security's default entry point for a
             * stateless config is Http403ForbiddenEntryPoint - so a
             * request with NO token returns 403 rather than 401, which
             * is both wrong and confusing to debug.
             *
             *   401 - no valid token was presented
             *   403 - valid token, insufficient role
             */
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authEntryPoint)
                    .accessDeniedHandler(authEntryPoint))

            // Our filter runs before the username/password filter, so a
            // request arrives at authorization already authenticated.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS for the Vite dev server.
     *
     * ANY PORT ON LOOPBACK, NOT ONE FIXED PORT. Vite claims 5173 if it
     * is free and silently moves to 5174, 5175 and so on if it is not -
     * and a one-port allowlist then rejects the browser's preflight.
     * The symptom is misleading: the OPTIONS request reaches the server
     * and is refused, the real request is never sent, and the frontend
     * reports "cannot reach the server" because a browser surfaces a
     * blocked response as a network error rather than an HTTP status.
     *
     * setAllowedOriginPatterns accepts a port wildcard; setAllowedOrigins
     * does not. This is still an allowlist, not "*" (NFR-9): only
     * loopback is permitted, so nothing off this machine can call the
     * API from a browser. A real deployment would name its actual
     * origin here, from configuration rather than a constant.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:[*]",
                "http://127.0.0.1:[*]"));
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
