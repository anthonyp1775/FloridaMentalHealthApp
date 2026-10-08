package com.flmentalhealth.controller;

import com.flmentalhealth.dto.Dtos.AuthDtos;
import com.flmentalhealth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The only two endpoints that do not require a token - you cannot
 * present one before you have one.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration and login")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** @Valid is what triggers the annotations on the request record. */
    @PostMapping("/register")
    @Operation(summary = "Create an account and receive a token")
    public ResponseEntity<AuthDtos.AuthResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {

        AuthDtos.AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a token")
    public ResponseEntity<AuthDtos.AuthResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }
}
