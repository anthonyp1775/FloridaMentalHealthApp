package com.flmentalhealth.service;

import com.flmentalhealth.TestFixtures;
import com.flmentalhealth.dto.Dtos.AuthDtos;
import com.flmentalhealth.entity.Role;
import com.flmentalhealth.entity.User;
import com.flmentalhealth.exception.ApiExceptions.DuplicateResourceException;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.repository.RoleRepository;
import com.flmentalhealth.repository.UserRepository;
import com.flmentalhealth.security.JwtService;
import com.flmentalhealth.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService - registration and login.
 *
 * Two of these tests are about security rather than behavior, and they
 * are the reason the class is worth testing at all:
 *
 *   register_grantsOnlyRoleUser - nobody can make themselves an
 *   administrator through the public registration endpoint.
 *
 *   login_failureMessageRevealsNothing - a failed login says the same
 *   thing whether the account exists or not. Distinguishing the two
 *   would let someone enumerate which email addresses are registered.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService service;

    private static final String RAW_PASSWORD = "CorrectHorse42";
    private static final String HASHED = "$2a$11$exampleHashValueThatIsSixtyCharactersLongForRealism0000";

    private AuthDtos.RegisterRequest registration() {
        return new AuthDtos.RegisterRequest(
                "Alicia", "Moreno", "alicia.moreno@example.com", RAW_PASSWORD);
    }

    // =================================================================
    // Registration
    // =================================================================

    /**
     * The raw password must never reach the entity. This asserts on the
     * object handed to save(), which is the last point before it would
     * have been written to a column.
     */
    @Test
    @DisplayName("hashes the password before the entity is ever saved")
    void register_hashesPasswordBeforeSaving() {
        Role userRole = TestFixtures.role(1L, "ROLE_USER");

        when(userRepository.existsByEmail("alicia.moreno@example.com"))
                .thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(100L);         // stands in for @GeneratedValue
            return saved;
        });
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("a.jwt.token");

        service.register(registration());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User persisted = captor.getValue();

        assertThat(persisted.getPassword()).isEqualTo(HASHED);
        assertThat(persisted.getPassword()).isNotEqualTo(RAW_PASSWORD);
        verify(passwordEncoder).encode(RAW_PASSWORD);
    }

    /**
     * Privilege is granted by a navigator, never self-assigned. If this
     * ever fails, anyone could register their way into the whole
     * referral queue.
     */
    @Test
    @DisplayName("grants ROLE_USER only - never ROLE_ADMIN")
    void register_grantsOnlyRoleUser() {
        Role userRole = TestFixtures.role(1L, "ROLE_USER");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(any())).thenReturn(HASHED);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("a.jwt.token");

        AuthDtos.AuthResponse response = service.register(registration());

        assertThat(response.roles()).containsExactly("ROLE_USER");
        assertThat(response.roles()).doesNotContain("ROLE_ADMIN");
        verify(roleRepository, never()).findByName("ROLE_ADMIN");
    }

    @Test
    @DisplayName("returns a token so the client is signed in immediately")
    void register_returnsTokenAndIdentity() {
        Role userRole = TestFixtures.role(1L, "ROLE_USER");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(any())).thenReturn(HASHED);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("a.jwt.token");

        AuthDtos.AuthResponse response = service.register(registration());

        assertThat(response.token()).isEqualTo("a.jwt.token");
        assertThat(response.email()).isEqualTo("alicia.moreno@example.com");
        assertThat(response.fullName()).isEqualTo("Alicia Moreno");
    }

    @Test
    @DisplayName("a second account on the same email is a 409")
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("alicia.moreno@example.com"))
                .thenReturn(true);

        var req = registration();
        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
        // The password is not even hashed on this path - no wasted work,
        // and no raw password passed around after the decision is made.
        verify(passwordEncoder, never()).encode(any());
    }

    /**
     * A missing ROLE_USER row means the seed never ran. Failing with a
     * message that says so beats a constraint violation thirty lines
     * deep in a Hibernate stack trace.
     */
    @Test
    @DisplayName("a missing ROLE_USER row says to run the seed")
    void register_throwsWhenDefaultRoleMissing() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());

        var req = registration();
        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("run the seed script");
    }

    // =================================================================
    // Login
    // =================================================================

    @Test
    @DisplayName("returns a token and every role the account holds")
    void login_returnsTokenAndRoles() {
        User navigator = TestFixtures.navigator(200L);
        UserPrincipal principal = new UserPrincipal(navigator);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateToken(principal)).thenReturn("navigator.jwt.token");

        AuthDtos.AuthResponse response = service.login(
                new AuthDtos.LoginRequest("navigator@carepathfl.org", RAW_PASSWORD));

        assertThat(response.token()).isEqualTo("navigator.jwt.token");
        assertThat(response.fullName()).isEqualTo("Dana Whitfield");
        assertThat(response.roles())
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    /**
     * ACCOUNT ENUMERATION. "No such user" and "wrong password" must be
     * indistinguishable from outside, and the original message must not
     * leak through - it often contains the email that was tried.
     */
    @Test
    @DisplayName("an unknown email and a wrong password give the identical message")
    void login_failureMessageRevealsNothing() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new UsernameNotFoundException(
                        "User not found: ghost@nowhere.test"));

        var req = new AuthDtos.LoginRequest("ghost@nowhere.test", "anything");
        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email or password is incorrect")
                // The address that was tried must not survive into the
                // response the caller sees.
                .hasMessageNotContaining("ghost@nowhere.test");
    }

    @Test
    @DisplayName("a wrong password gives that same message, and no token")
    void login_wrongPasswordIssuesNoToken() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        var req = new AuthDtos.LoginRequest("alicia.moreno@example.com", "wrong");
        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email or password is incorrect");

        verify(jwtService, never()).generateToken(any());
    }

    /**
     * The comparison happens inside Spring Security's
     * AuthenticationManager, which means this service never reads a
     * stored hash. Verifying the delegation is the point.
     */
    @Test
    @DisplayName("delegates credential checking rather than comparing hashes itself")
    void login_delegatesToAuthenticationManager() {
        User client = TestFixtures.client(100L);
        UserPrincipal principal = new UserPrincipal(client);

        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
        when(jwtService.generateToken(principal)).thenReturn("client.jwt.token");

        service.login(new AuthDtos.LoginRequest(
                "alicia.moreno@example.com", RAW_PASSWORD));

        ArgumentCaptor<Authentication> captor =
                ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());

        assertThat(captor.getValue().getPrincipal())
                .isEqualTo("alicia.moreno@example.com");
        assertThat(captor.getValue().getCredentials()).isEqualTo(RAW_PASSWORD);

        // The service never calls the encoder on the login path.
        verify(passwordEncoder, never()).matches(any(), any());
    }
}
