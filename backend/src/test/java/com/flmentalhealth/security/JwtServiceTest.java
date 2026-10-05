package com.flmentalhealth.security;

import com.flmentalhealth.TestFixtures;
import com.flmentalhealth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtService - the thing that decides whether a request is who it says
 * it is.
 *
 * No mocks here: the service is constructed directly with a secret and
 * an expiry, which is how it is built in production too. Mocking the
 * signing would defeat the point, because the signature is the whole
 * security property - these tests cut real tokens and then try to break
 * them.
 *
 * The three that matter are tampering, expiry, and subject mismatch.
 * Each is a way someone could try to get in with a token that should
 * not work, and each must come back false rather than throwing: an
 * invalid token is an ordinary condition on a public endpoint, not an
 * exceptional one.
 */
@DisplayName("JwtService")
class JwtServiceTest {

    /** 64 characters - HMAC-SHA256 needs at least 32 bytes of key. */
    private static final String SECRET =
            "test-secret-that-is-long-enough-for-hmac-sha256-0123456789abcdef";

    private static final long ONE_HOUR = 3_600_000L;

    private JwtService jwtService;
    private UserDetails client;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, ONE_HOUR);
        client = TestFixtures.principal(TestFixtures.client(100L));
    }

    /** Flips one character so the token is guaranteed to differ. */
    private static String tamperWith(String token) {
        char[] chars = token.toCharArray();
        int last = chars.length - 1;
        chars[last] = (chars[last] == 'A') ? 'B' : 'A';
        return new String(chars);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        /**
         * A short key would still "work" - it would sign and verify -
         * but it would be weak enough to brute force. Failing at
         * startup beats discovering that in production.
         */
        @Test
        @DisplayName("a secret under 32 bytes is refused outright")
        void rejectsShortSecret() {
            assertThatThrownBy(() -> new JwtService("too-short", ONE_HOUR))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 32 bytes");
        }

        @Test
        @DisplayName("exactly 32 bytes is accepted")
        void acceptsMinimumLengthSecret() {
            String thirtyTwo = "0123456789abcdef0123456789abcdef";
            assertThat(thirtyTwo).hasSize(32);

            assertThat(new JwtService(thirtyTwo, ONE_HOUR)).isNotNull();
        }
    }

    @Nested
    @DisplayName("issuing")
    class Issuing {

        @Test
        @DisplayName("a token carries the email as its subject")
        void token_subjectIsTheEmail() {
            String token = jwtService.generateToken(client);

            assertThat(jwtService.extractEmail(token))
                    .isEqualTo("alicia.moreno@example.com");
        }

        /**
         * Roles ride along as a claim so the filter can rebuild
         * authorities without hitting the database on every request.
         * That is also why a role change needs a fresh login to take
         * effect - the token carries the roles it was issued with.
         */
        /*
         * The claim comes back as a RAW List, because Claims.get takes a
         * Class token and List.class carries no element type. Declaring
         * the variable as List<String> is an unchecked conversion - a
         * warning, suppressed here - and it is what lets AssertJ compare
         * against Strings. A List<?> would not: a wildcard capture
         * accepts no concrete argument type, so containsExactlyInAnyOrder
         * would not compile.
         */
        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("roles travel in the token, so no database hit is needed to read them")
        void token_carriesRoles() {
            UserDetails navigator =
                    TestFixtures.principal(TestFixtures.navigator(200L));

            String token = jwtService.generateToken(navigator);

            List<String> roles = jwtService.extractClaim(
                    token, claims -> claims.get("roles", List.class));

            assertThat(roles).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        }

        @Test
        @DisplayName("the expiry is set ahead, by the configured amount")
        void token_expiryIsInTheFuture() {
            Date expiry = jwtService.extractExpiration(
                    jwtService.generateToken(client));

            assertThat(expiry).isAfter(new Date());
            assertThat(expiry).isBefore(new Date(System.currentTimeMillis()
                    + ONE_HOUR + 10_000L));
        }

        /**
         * A JWT payload is base64-encoded, NOT encrypted - anyone
         * holding a token can read it. So nothing sensitive goes in,
         * and in particular the password hash must not.
         */
        @Test
        @DisplayName("no password material is anywhere in the token")
        void token_carriesNothingSensitive() {
            User user = TestFixtures.client(100L);
            String token = jwtService.generateToken(TestFixtures.principal(user));

            assertThat(token).doesNotContain(user.getPassword());
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("a token this service issued, for this user, is valid")
        void valid_forItsOwnUser() {
            assertThat(jwtService.isTokenValid(
                    jwtService.generateToken(client), client)).isTrue();
        }

        /**
         * A token belonging to someone else must not authenticate this
         * user, even though the signature is perfectly good.
         */
        @Test
        @DisplayName("a valid token for a DIFFERENT user is rejected")
        void invalid_whenSubjectDoesNotMatch() {
            String navigatorToken = jwtService.generateToken(
                    TestFixtures.principal(TestFixtures.navigator(200L)));

            assertThat(jwtService.isTokenValid(navigatorToken, client)).isFalse();
        }

        /**
         * THE SIGNATURE IS THE WHOLE POINT. Change a single character
         * and verification must fail - otherwise anyone could edit the
         * roles claim and promote themselves.
         */
        @Test
        @DisplayName("a token with one character changed is rejected")
        void invalid_whenTampered() {
            String tampered = tamperWith(jwtService.generateToken(client));

            assertThat(jwtService.isTokenValid(tampered, client)).isFalse();
        }

        /**
         * A token signed with a different key must not verify here.
         * This is what stops a token minted by some other system - or
         * by an attacker guessing at the format - from being accepted.
         */
        @Test
        @DisplayName("a token signed with another key is rejected")
        void invalid_whenSignedWithAnotherKey() {
            JwtService other = new JwtService(
                    "a-completely-different-secret-key-also-long-enough-0123456789",
                    ONE_HOUR);

            String foreignToken = other.generateToken(client);

            assertThat(jwtService.isTokenValid(foreignToken, client)).isFalse();
        }

        /** Negative expiry issues a token that was already stale. */
        @Test
        @DisplayName("an expired token is rejected")
        void invalid_whenExpired() {
            JwtService alreadyExpired = new JwtService(SECRET, -1000L);

            String staleToken = alreadyExpired.generateToken(client);

            assertThat(alreadyExpired.isTokenValid(staleToken, client)).isFalse();
        }

        @Test
        @DisplayName("nonsense is rejected rather than throwing")
        void invalid_whenNotAToken() {
            assertThat(jwtService.isTokenValid("not.a.token", client)).isFalse();
            assertThat(jwtService.isTokenValid("", client)).isFalse();
            assertThat(jwtService.isTokenValid("aaaa", client)).isFalse();
        }
    }
}
