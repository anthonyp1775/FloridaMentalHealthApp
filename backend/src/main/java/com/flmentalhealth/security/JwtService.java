package com.flmentalhealth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Creates and validates JSON Web Tokens.
 *
 * A JWT has three parts: a header, a payload of claims, and a
 * signature. The payload is only base64-encoded, NOT encrypted -
 * anyone holding a token can read it. What the signature guarantees is
 * that nobody has *changed* it, because altering a single byte
 * invalidates the signature and this service rejects the token.
 *
 * That is why no sensitive data goes in here. The payload carries who
 * the user is and what roles they hold - nothing that would matter if
 * it were read.
 */
/*
 * java.util.Date is not a choice here. jjwt's builder and parser take
 * Date and have no java.time overload (jwtk/jjwt#577, closed as a
 * duplicate of #235 - the documented approach is to convert yourself).
 * Every Date in this class exists at that boundary and nowhere else.
 *
 * S2143 is raised at file level (on the import, outside the class body),
 * so @SuppressWarnings cannot reach it. It is marked Accepted in
 * SonarQube with this same reasoning.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    /**
     * Secret and expiry come from configuration, never from code. In
     * the prod profile they resolve from environment variables, which
     * is NFR-6 ("no secrets in source control").
     */
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        // HMAC-SHA256 requires at least 256 bits of key material. Failing
        // loudly at startup beats discovering a weak key in production.
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes (256 bits); got "
                            + keyBytes.length);
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    // ---------- creating ----------

    /**
     * Issues a token for an authenticated user.
     *
     * The subject is the email, because that is this system's login
     * identifier. Roles ride along as a claim so the filter can rebuild
     * the user's authorities without a database hit on every request.
     */
    public String generateToken(UserDetails user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        List<String> roles = user.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    // ---------- reading ----------

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    /**
     * Parses and verifies in one step. parseSignedClaims throws if the
     * signature does not match, so any token that survives this call
     * was issued by us and has not been tampered with.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ---------- validating ----------

    /**
     * A token is valid when it parses, its signature verifies, it has
     * not expired, and its subject matches the user we loaded.
     *
     * Returns false rather than throwing: an invalid token is an
     * ordinary condition on a public internet endpoint, not an
     * exceptional one, and the filter simply leaves the request
     * unauthenticated.
     */
    public boolean isTokenValid(String token, UserDetails user) {
        try {
            final String email = extractEmail(token);
            return email.equals(user.getUsername()) && !isExpired(token);
        } catch (JwtException | IllegalArgumentException _) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException _) {
            return true;
        }
    }
}
