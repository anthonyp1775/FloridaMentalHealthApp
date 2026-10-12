package com.flmentalhealth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request, before the authorization filter.
 *
 * Reads "Authorization: Bearer <token>", validates it, and puts the
 * authenticated user into the SecurityContext so that hasRole(...),
 * @PreAuthorize and the controllers can see who is asking.
 *
 * It never rejects anything itself. If there is no token, or the token
 * is bad, it simply leaves the context empty and passes the request
 * along - the AuthorizationFilter further down the chain is what
 * decides whether an unauthenticated request is allowed through. That
 * separation is what lets the public endpoints work.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String header = request.getHeader(HEADER);

        // No bearer token: nothing to do, let the chain decide.
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = header.substring(PREFIX.length());

        try {
            final String email = jwtService.extractEmail(token);

            // Skip if this request is already authenticated.
            if (email != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails user = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(token, user)) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user,
                            null,            // credentials - never kept after login
                            user.getAuthorities());

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception _) {
            /*
             * A malformed, expired or forged token is an ordinary thing
             * to receive on a public endpoint, not an error worth
             * failing the request over here. Clear the context and
             * continue unauthenticated; the authorization filter will
             * return 401 if the endpoint required a login.
             */
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
