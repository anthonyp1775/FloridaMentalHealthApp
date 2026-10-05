# ADR 0001 — JWT instead of session-based authentication

## Status
Accepted

## Context

The React frontend is served separately from the API — a different
origin in development, and a different host in any real deployment. A
session-cookie approach across origins means `withCredentials`, a CORS
configuration that permits credentials, `SameSite` handling, and sticky
sessions or a shared session store the moment there is more than one
instance of the API.

Earlier coursework used session-based authentication with Thymeleaf,
where the server rendered the pages and the browser and the application
shared an origin by definition. That assumption does not hold here.

The API also has two distinct audiences — a browser application and an
API client used for testing — and a mechanism that works for both
without special-casing is worth more than one tuned for browsers.

## Decision

Stateless JWT authentication, with `SessionCreationPolicy.STATELESS`.

- A token is issued on login and on registration, signed with HMAC-SHA256.
- The token carries the user's email as subject and their roles as a claim.
- Every request presents it as `Authorization: Bearer <token>`.
- `JwtAuthenticationFilter` validates it and populates the
  `SecurityContext` for that request only.
- No `HttpSession` is ever created or consulted.

CSRF protection is disabled, which is correct here rather than lax: CSRF
exists to stop a browser silently attaching an ambient credential to a
forged request. There is no session and no auth cookie — the token is
attached by our own JavaScript — so there is no ambient credential to
forge with.

## Consequences

**Gained**

- No server-side session store, and no sticky sessions. Any instance can
  serve any request, which is what makes the API horizontally scalable
  (NFR-19).
- The same mechanism serves the browser app and the API test collection
  with no difference in handling.
- Roles travel in the token, so authorization needs no database read on
  each request.

**Accepted costs**

- **A token cannot be revoked before it expires** without adding a
  denylist, which would reintroduce the server-side state this decision
  removes. Mitigated by a 24-hour expiry. A production system handling
  real accounts would need refresh tokens and revocation; this one does
  not, and pretending otherwise would be worse than saying so.
- **A role change does not affect a token already issued.** This is not
  a bug and it surfaced during development: adding `ROLE_ADMIN` in the
  database had no effect until the user logged in again. The token
  carries the roles it was issued with, by design.
- The payload is base64-encoded, **not encrypted** — anyone holding a
  token can read it. Nothing sensitive goes in it. `JwtServiceTest`
  asserts that the password hash appears nowhere in a generated token.

## Verification

`JwtServiceTest` cuts real tokens and then attempts to break them:
tampered by one character, signed with a different key, expired, and
belonging to a different user. Each must be rejected, and must return
`false` rather than throw — an invalid token is an ordinary condition on
a public endpoint, not an exceptional one.
