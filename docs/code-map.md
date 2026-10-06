# Code Map — Florida Mental Health App

What every folder is for, in the order a request travels through it.
Written to be read aloud: each section ends with the question a grader
is most likely to ask about that folder.

---

## The two halves

```
backend/     Spring Boot REST API      :8080
frontend/    React single-page app     :5173
```

The dependency runs one way. The frontend calls the backend over HTTP
and knows nothing about Hibernate, SQL, or Java types. The backend has
never heard of React. Either could be replaced without touching the
other, and the contract between them is `docs/api-design.md`.

---

# Backend

`backend/src/main/java/com/flmentalhealth/` — 4,795 lines across 8
packages, plus `FloridaMentalHealthApplication.java`, the entry point.
A request passes through them in this order:

```
HTTP request
   ↓
security/      is this a real token? who is it?
   ↓
controller/    parse input, hand off
   ↓
service/       apply the business rules      ← @Transactional starts
   ↓
repository/    talk to the database
   ↓
entity/        the rows themselves
   ↓
dto/           shape the answer
   ↓
exception/     if anything threw, turn it into a status code
   ↓
HTTP response
```

### `security/` — 5 files, 447 lines

Authentication and identity. `JwtService` signs and verifies tokens
(HMAC-SHA256). `JwtAuthenticationFilter` runs once per request, reads
the `Authorization` header, and puts the user into Spring's security
context. `UserPrincipal` is what the rest of the app sees as "the
current user." `CustomUserDetailsService` loads a user by email for
login. `JwtAuthEntryPoint` returns a 401 as JSON instead of redirecting
to a login page, which a REST client has no use for.

No session, no server-side store. The token carries the identity, so any
instance can serve any request.

> **Likely question:** *"Where does the user id come from on a request?"*
> The token, via the security context — never from a path variable,
> query parameter, or body field. That's why passing someone else's id
> does nothing: the server never reads one.

### `controller/` — 6 files, 525 lines

The HTTP layer, and deliberately the thinnest. Each method parses input,
calls one service method, and returns a DTO. There is no business logic
here and no entity ever leaves. 32 endpoints across six controllers:
`Auth`, `Catalog`, `Profile`, `Provider`, `Referral`, `Report`.

> **Likely question:** *"Why so little code in the controllers?"*
> So the rules live in one place. If validation lived here it would have
> to be repeated for every caller; in the service it's enforced on every
> path, including the tests.

### `service/` — 6 files, 1,247 lines

The business rules, and the `@Transactional` boundary. This is where the
project's actual thinking lives.

| Service | What it owns |
|---|---|
| `AuthService` | `register`, `login` — hashing, duplicate email, token issue |
| `ProviderService` | `search`, `save`/`unsave`, admin CRUD, `updateCapacity` |
| `ReferralService` | `submit`, `withdraw`, `queue`, `decide` — the workflow |
| `CatalogService` | the reference lists (counties, specialties, languages…) |
| `ProfileService` | read and update the client profile |
| `ReportService` | `referralSummary`, `accessGap`, `countyCapacity` |

`ReferralService.decide()` is the one to know. Accepting a referral has
to check capacity and decrement it without two admins both accepting the
last open slot, so it reads the provider under a pessimistic write lock
and downgrades to waitlist if capacity is gone.

> **Likely question:** *"What happens if two admins accept the same last
> slot at the same time?"* One wins; the other's referral is waitlisted
> rather than overbooking the provider. `findByIdForUpdate` takes a row
> lock, so the second transaction waits for the first to commit and then
> sees the real count. There's a test for exactly this.

### `repository/` — 13 files, 343 lines

Spring Data JPA interfaces — one per entity. Most are empty, because
`JpaRepository` already provides what they need and inventing finder
methods nothing calls is clutter. Two carry real work: the provider
search query, and `findByIdForUpdate` with the `PESSIMISTIC_WRITE` lock
hint above.

> **Likely question:** *"Why are some of these empty?"*
> Each empty one has a comment saying why. The clearest case is
> `ReferralStatusHistoryRepository`: history rows are written through
> `ReferralRequest.addHistory()` and read as part of the referral that
> owns them. Giving it finders would invite reading the audit trail away
> from the record it explains.

### `entity/` — 13 files, 1,421 lines

The JPA model: `User`, `Role`, `Provider`, `Organization`, `County`,
`Specialty`, `Language`, `Population`, `InsurancePlan`, `ClientProfile`,
`SavedProvider`, `ReferralRequest`, `ReferralStatusHistory`.

The largest package, because it holds the relationships, the database
constraints, and the small amount of behavior that belongs on the data
itself rather than in a service — `ReferralRequest.addHistory()` keeps a
status change and its audit row inseparable.

> **Likely question:** *"Why is `equals`/`hashCode` written by hand?"*
> Lombok's generated version uses every field, which breaks for an
> entity whose id is assigned on insert — the same object hashes
> differently before and after saving. These compare by id only, and
> there's a parameterized test across all 13 entities for it.

### `dto/` — 6 files, 228 lines

Java records: the request and response shapes. Requests carry the
validation annotations (`@NotBlank`, `@Email`, `@Size`). Responses carry
only the fields a client should see.

Entities are never serialized. That's what keeps a password hash out of
a JSON body and stops a lazy association from triggering a query halfway
through writing the response — which is also why
`spring.jpa.open-in-view=false` is set.

> **Likely question:** *"Why not just return the entity?"*
> Two reasons, and the second is the one people forget: it leaks fields,
> and it makes the database schema part of your public API, so a column
> rename becomes a breaking change for the frontend.

### `exception/` — 2 files, 296 lines

`ApiExceptions` holds the typed exceptions a service throws
(`NotFoundException`, `ConflictException`, and so on).
`GlobalExceptionHandler` is a `@RestControllerAdvice` that maps each one
to a status code and a consistent error body, so every failure in the
API has the same shape.

It declares a handler per case rather than relying on Spring's defaults,
because two of them needed it: `HttpRequestMethodNotSupportedException`
implements `ErrorResponse` but doesn't extend `ErrorResponseException`,
so a generic handler never matched it and the catch-all reported 500
instead of 405. `NoResourceFoundException` had the same problem, which
turned every 404 into a server error.

> **Likely question:** *"How does the frontend know what went wrong?"*
> Every error returns the same JSON shape, so `api.js` has one place
> that reads it. Validation failures add a per-field map on top, keeping
> the first five fields identical so a client can parse either the same
> way.

### `config/` — 3 files, 270 lines

`SecurityConfig` — the filter chain: which paths are public
(`/api/auth/register`, `/api/auth/login`, Swagger), which need a role,
CORS, and the BCrypt encoder. `DataSeeder` loads `schema.sql` and
`seed.sql` on dev startup. `OpenApiConfig` supplies the Swagger metadata
and registers the bearer scheme so the UI has an Authorize button.

### `resources/`

```
application.properties        shared settings
application-dev.properties    local MySQL, seeding on, SQL logging
application-prod.properties   env-var config, seeding off
db/schema.sql                 tables, keys, constraints
db/seed.sql                   67 counties, 17 orgs, 35 providers
```

---

# Frontend

`frontend/src/` — 30 files.

```
main.jsx         mounts React, wraps the app in the providers
App.jsx          the routes
api.js           every HTTP call
hooks.js         useFetch, useDebounce
utils.js         labels and formatting
resources.js     crisis line data (verified 2026-10-04)
context/         state that many screens need
components/      reusable pieces
pages/           one per route
index.css        design tokens and layout
```

### `api.js` — 103 lines

One axios instance for the whole app, with two interceptors: one attaches
the token to every request, one turns any failure into an `Error` with a
readable message. The result is that no component ever thinks about
tokens or about what an error looks like — it calls a function and gets
data or an exception. Exports six grouped objects: `authApi`,
`profileApi`, `catalogApi`, `providerApi`, `referralApi`, `reportApi`.

> **Likely question:** *"Where is the token stored and attached?"*
> `tokenStore` in this file, and the request interceptor adds the header.
> One place, so logging out or expiry is handled once.

### `context/` — 2 files

`AuthContext` holds who is logged in and exposes `login`/`logout`;
`ProtectedRoute` and the nav read from it. `ThemeContext` handles
light/dark — it reads `prefers-color-scheme`, then persists an explicit
override, so someone with light sensitivity isn't forced into a bright
screen.

### `components/` — 10 files

`Layout` (nav and shell), `ProtectedRoute` (redirects when not logged in
or not an admin), `CrisisBanner` (always reachable), `ProviderCard`,
`SearchFilters`, `ReferralForm`, `StatusTimeline`, `Table`, `Modal`, and
`ui.jsx` — eleven small primitives (`Button`, `Field`, `Input`, `Select`,
`Tag`, `Card`, `Spinner`, `Notice`, `Empty`…) kept in one file because
each is a few lines and splitting them would mean more imports than code.

### `pages/` — 11 files

One per route: `AuthPage`, `SearchPage`, `ProviderDetailPage`,
`SavedPage`, `MyReferralsPage`, `ProfilePage`,
`StatewideResourcesPage`, `AdminQueuePage`, `AdminProvidersPage`,
`ReportsPage`, `NotFoundPage`.

`SearchPage` is the one to know. It holds seven filters plus pagination
in a single `useReducer` instead of seven `useState` calls, because
changing any filter must also reset the page to 0 — one transition, not
seven places to forget it.

> **Likely question:** *"Why `useReducer` here and `useState` elsewhere?"*
> Because these values change together. Any filter change resets
> pagination; with separate state that rule has to be repeated at every
> call site, and the bug it causes — page 3 of a result set with 1 page —
> is invisible until someone filters while deep in the results.

---

## The rules that hold it together

Four conventions, applied everywhere. Each is a question with one answer
instead of a judgement call per file.

1. **Entities never leave the service layer.** Controllers return DTOs.
2. **The current user comes from the token.** Never from the request.
3. **Business rules live in services.** Controllers parse; services decide.
4. **Every error has the same shape.** One handler, one body.

---

## Where the tests sit

`backend/src/test/java/com/flmentalhealth/` — 186 test methods in 13
classes, running 258 cases (three classes are parameterized),
mirroring the main packages: services with mocked repositories,
controllers through standalone MockMvc, entities with no mocks at all,
and `JwtServiceTest` cutting real tokens and then trying to break them.

See `docs/testing.md` for the split and for the one coverage boundary
worth stating out loud: `@PreAuthorize` is not active under standalone
MockMvc, so role enforcement is verified through `SecurityConfig` and
the service layer rather than the controller tests.
