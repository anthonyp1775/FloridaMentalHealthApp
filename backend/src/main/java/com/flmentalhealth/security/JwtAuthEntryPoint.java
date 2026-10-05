package com.flmentalhealth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flmentalhealth.dto.ReportDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Makes authentication and authorization failures return the same JSON
 * shape as every other error, and - more importantly - makes them
 * return the RIGHT status code.
 *
 * WHY THIS EXISTS: with form login and HTTP Basic disabled (as they
 * must be for a stateless API), Spring Security's default entry point
 * is Http403ForbiddenEntryPoint. That means a request with no token at
 * all comes back 403, which reads as "you are logged in but not
 * allowed" when the truth is "you are not logged in". Debugging that
 * distinction matters, and the API contract promises:
 *
 *   401 - missing, malformed or expired token
 *   403 - valid token, insufficient role
 */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** No valid credentials were presented -> 401. */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        write(response, request, HttpStatus.UNAUTHORIZED,
              "Authentication required - provide a valid bearer token");
    }

    /** Authenticated, but the role is not sufficient -> 403. */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        write(response, request, HttpStatus.FORBIDDEN,
              "You do not have access to this resource");
    }

    private void write(HttpServletResponse response,
                       HttpServletRequest request,
                       HttpStatus status,
                       String message) throws IOException {

        ReportDtos.ErrorResponse body = new ReportDtos.ErrorResponse(
                LocalDateTime.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
