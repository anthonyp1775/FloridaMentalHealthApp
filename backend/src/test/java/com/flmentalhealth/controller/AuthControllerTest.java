package com.flmentalhealth.controller;

import com.flmentalhealth.dto.Dtos.AuthDtos;
import com.flmentalhealth.exception.ApiExceptions.DuplicateResourceException;
import com.flmentalhealth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.flmentalhealth.controller.ControllerTestSupport.json;
import static com.flmentalhealth.controller.ControllerTestSupport.mockMvcFor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController over HTTP.
 *
 * Three things are being checked that a service test cannot reach: the
 * status codes (201 for register, 200 for login), that @Valid actually
 * rejects a malformed body before the service is ever called, and that
 * the exception handler turns each exception into the right status with
 * a readable message.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // No signed-in user: these are the only two endpoints that work
        // without a token.
        mockMvc = mockMvcFor(new AuthController(authService), null);
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 with a token")
    void login_returns200WithToken() throws Exception {
        when(authService.login(any())).thenReturn(new AuthDtos.AuthResponse(
                "a.jwt.token", "alicia.moreno@example.com",
                "Alicia Moreno", List.of("ROLE_USER")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AuthDtos.LoginRequest(
                                "alicia.moreno@example.com", "CorrectHorse42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("a.jwt.token"))
                .andExpect(jsonPath("$.fullName").value("Alicia Moreno"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 201, not 200")
    void register_returns201() throws Exception {
        when(authService.register(any())).thenReturn(new AuthDtos.AuthResponse(
                "a.jwt.token", "new.person@example.com",
                "New Person", List.of("ROLE_USER")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AuthDtos.RegisterRequest(
                                "New", "Person", "new.person@example.com",
                                "CorrectHorse42"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    /**
     * Wrong password and unknown account both arrive here as the same
     * generic BadCredentialsException, and both come back as 401 with
     * the same message. See AuthServiceTest for why.
     */
    @Test
    @DisplayName("bad credentials are 401, with nothing about which part was wrong")
    void login_badCredentialsIs401() throws Exception {
        when(authService.login(any())).thenThrow(
                new BadCredentialsException("Email or password is incorrect"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AuthDtos.LoginRequest(
                                "alicia.moreno@example.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Email or password is incorrect"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("a duplicate email is 409")
    void register_duplicateEmailIs409() throws Exception {
        when(authService.register(any())).thenThrow(
                new DuplicateResourceException(
                        "An account already exists for alicia.moreno@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AuthDtos.RegisterRequest(
                                "Alicia", "Moreno", "alicia.moreno@example.com",
                                "CorrectHorse42"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "An account already exists for alicia.moreno@example.com"));
    }

    /**
     * Validation runs before the service does. The response carries a
     * fieldErrors map so a form can mark the specific input rather than
     * showing one message at the top.
     */
    @Test
    @DisplayName("a malformed email is 400 with per-field detail, and never reaches the service")
    void login_invalidEmailIs400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AuthDtos.LoginRequest(
                                "not-an-email", "CorrectHorse42"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("a password under 8 characters is rejected at the boundary")
    void register_shortPasswordIs400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AuthDtos.RegisterRequest(
                                "New", "Person", "new.person@example.com", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("an unreadable body is 400, not 500")
    void login_malformedJsonIs400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json "))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }
}
