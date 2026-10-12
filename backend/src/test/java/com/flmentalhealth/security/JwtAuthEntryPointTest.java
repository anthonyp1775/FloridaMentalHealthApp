package com.flmentalhealth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flmentalhealth.dto.Dtos.ReportDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtAuthEntryPoint - the reason a missing token returns 401 and not
 * 403.
 *
 * With form login and HTTP Basic disabled, Spring Security's default
 * entry point is Http403ForbiddenEntryPoint, so an unauthenticated
 * request comes back 403 - "you are logged in but not allowed" - when
 * the truth is "you are not logged in". These tests pin the contract
 * the API documents:
 *
 *   401 - missing, malformed or expired token
 *   403 - valid token, insufficient role
 *
 * They also pin the body SHAPE: the same JSON every other error uses,
 * so a client has one error parser rather than two.
 */
@DisplayName("JwtAuthEntryPoint")
class JwtAuthEntryPointTest {

    private final JwtAuthEntryPoint entryPoint = new JwtAuthEntryPoint();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("no credentials -> 401 as JSON, never a redirect to a login page")
    void commence_returns401AsJson() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/referrals/mine");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response,
                new BadCredentialsException("no token presented"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON_VALUE);

        ReportDtos.ErrorResponse body = json.readValue(
                response.getContentAsByteArray(), ReportDtos.ErrorResponse.class);

        assertThat(body.status()).isEqualTo(401);
        assertThat(body.path()).isEqualTo("/api/referrals/mine");
        assertThat(body.message()).contains("bearer token");
        // The timestamp is populated rather than left null - clients and
        // log correlation both rely on it.
        assertThat(body.timestamp()).isNotBlank();
    }

    @Test
    @DisplayName("authenticated but under-privileged -> 403, a different status entirely")
    void handle_returns403AsJson() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/reports/access-gap");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.handle(request, response,
                new AccessDeniedException("ROLE_ADMIN required"));

        assertThat(response.getStatus()).isEqualTo(403);

        ReportDtos.ErrorResponse body = json.readValue(
                response.getContentAsByteArray(), ReportDtos.ErrorResponse.class);

        assertThat(body.status()).isEqualTo(403);
        assertThat(body.path()).isEqualTo("/api/reports/access-gap");
        // The message must not leak which role was required.
        assertThat(body.message()).doesNotContain("ROLE_ADMIN");
    }
}
