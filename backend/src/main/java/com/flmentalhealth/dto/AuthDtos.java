package com.flmentalhealth.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/** Grouped auth DTOs. Used as AuthDtos.LoginRequest, etc. */
public class AuthDtos {

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank @Size(max = 50) String firstName,
            @NotBlank @Size(max = 50) String lastName,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record AuthResponse(String token, String email,
                               String fullName, List<String> roles) {}
}
