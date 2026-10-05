package com.flmentalhealth.service;

import com.flmentalhealth.dto.AuthDtos;
import com.flmentalhealth.entity.Role;
import com.flmentalhealth.entity.User;
import com.flmentalhealth.exception.ApiExceptions.DuplicateResourceException;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.repository.RoleRepository;
import com.flmentalhealth.repository.UserRepository;
import com.flmentalhealth.security.JwtService;
import com.flmentalhealth.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Registration and login. */
@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Creates an account and returns a token, so the client is logged
     * in immediately rather than being bounced to a login form.
     *
     * New accounts always get ROLE_USER. There is deliberately no way
     * to request ROLE_ADMIN through this endpoint - privilege is
     * granted by a navigator, never self-assigned.
     */
    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "An account already exists for " + request.email());
        }

        Role userRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        DEFAULT_ROLE + " is missing - run the seed script"));

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        // The one place a raw password is ever touched, and it is hashed
        // before it reaches the entity.
        user.setPassword(passwordEncoder.encode(request.password()));
        user.addRole(userRole);

        User saved = userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(saved);
        return buildResponse(principal);
    }

    /**
     * Verifies credentials and issues a token.
     *
     * The AuthenticationManager does the actual comparison, which means
     * BCrypt matching happens in Spring Security rather than here - we
     * never read a stored hash in this class.
     */
    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(), request.password()));

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            return buildResponse(principal);

        } catch (org.springframework.security.core.AuthenticationException e) {
            /*
             * One message for every failure mode. Distinguishing "no
             * such account" from "wrong password" tells an attacker
             * which emails are registered, which is an account
             * enumeration leak.
             */
            throw new BadCredentialsException("Email or password is incorrect");
        }
    }

    private AuthDtos.AuthResponse buildResponse(UserPrincipal principal) {
        String token = jwtService.generateToken(principal);

        List<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();

        return new AuthDtos.AuthResponse(
                token,
                principal.getUsername(),
                principal.getUser().getFullName(),
                roles);
    }
}
