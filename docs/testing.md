# Testing — Florida Mental Health App

**135 tests, 9 classes.** Run them:

```bash
cd backend
mvn clean test
```

Coverage report: `backend/target/site/jacoco/index.html` (JaCoCo runs
automatically in the `test` phase).

No database is needed. Nothing here starts a Spring context, so there is
no datasource to configure and no JWT secret to supply — the whole suite
runs in a few seconds.

---

## The two layers, and why the split is where it is

| Layer | Classes | How | What it proves |
|---|---|---|---|
| Service | 6 | JUnit 5 + Mockito, repositories mocked | Business rules, in isolation from JPA and MySQL |
| Controller | 3 | MockMvc standalone, services mocked | Request mapping, binding, validation, status codes |
| End to end | — | Bruno collection, app running | The real filter chain, real tokens, real SQL |

**Service tests mock the repositories.** The rules being checked are
rules about the domain, not about Hibernate, so running them against a
database would make them slower and no more convincing.

**Controller tests use `MockMvcBuilders.standaloneSetup`** rather than
`@WebMvcTest`. Standalone wires one controller to the Spring MVC
machinery with no application context: no component scan, no datasource,
no `app.jwt.secret`. The tests cannot fail for a reason unrelated to the
controller under test.

The cost of that choice is explicit: **`@PreAuthorize` is not active in
these tests**, because method security is applied by a Spring proxy that
standalone setup does not build. Role enforcement is verified in the
Bruno collection against the running application, which is the honest
level for it — real tokens through the real filter chain, rather than a
mock of both. If asked "where do you test that a client cannot resolve a
referral?", the answer is Bruno request 5d, not JUnit.

Two small pieces of scaffolding:

- `TestFixtures` — entity builders. A Provider needs an Organization,
  which needs a County, before any service method can map it; without
  this, thirty test methods would each rebuild that chain.
- `ControllerTestSupport` — the MockMvc setup, including a
  `PrincipalResolver` that supplies the signed-in user. Spring
  Security's own `@AuthenticationPrincipal` resolver is not registered
  in standalone setup, so this stands in for it.

---

## The tests worth pointing at

### 1. `ReferralServiceTest.decide_downgradesToWaitlistWhenCapacityIsGone`

The navigator asks for `ACCEPTED`. The provider's last slot went between
the client submitting and the navigator reviewing. Overbooking would
promise an intake that does not exist; failing outright would lose the
request. The referral resolves to **`WAITLISTED`**, the waitlist count
goes up, open slots stay at zero, and the reason lands in the history
row.

`ReferralControllerTest.decide_reportsTheStatusActuallyApplied` is the
same case over HTTP: the body says `ACCEPTED`, the 200 says
`WAITLISTED`.

### 2. `ReferralServiceTest.decide_usesPessimisticReadForCapacity`

A mock cannot prove that `SELECT ... FOR UPDATE` takes a row lock — that
is SQL behavior, confirmed by reading the generated statement. What this
test proves is that the service **asks for the locking read** rather
than reusing the stale provider hanging off the referral. That is the
part a well-meaning refactor would quietly break, and the symptom would
be two navigators both consuming the same last slot.

### 3. `ProviderControllerTest.search_withNoParametersBindsAndSucceeds`

A bare `/api/providers/search` — what the frontend sends on first load —
must return the unfiltered page. In development it returned **400**: one
criterion was a primitive `boolean`, and Spring cannot bind an absent
query parameter to a primitive. That is a binding failure, so it can
only be caught at the controller layer. Every service test passed the
whole time it was broken.

### 4. The capacity invariant, tested on all three write paths

A provider can never be "accepting new clients" with zero open slots.
`ProviderServiceTest` checks it through `updateCapacity`, through
`create`, and through `update`, because the rule is enforced on every
write path and a test per path is what keeps it that way.

### 5. `AuthServiceTest.login_failureMessageRevealsNothing`

An unknown email and a wrong password produce the identical message, and
the original exception text — which contains the address that was tried
— does not survive into the response. Telling the two apart would let
someone enumerate which emails have accounts.

### 6. `AuthServiceTest.register_grantsOnlyRoleUser`

Nobody can make themselves an administrator through public registration.
If this ever fails, anyone can register their way into the referral
queue.

### 7. `ReportServiceTest.referralSummary_toDateIncludesTheWholeDay`

A range of 1–30 September has to include a referral submitted at 4pm on
the 30th. Treating the end date as midnight silently drops a day — a
report that is quietly wrong rather than obviously broken.

---

## One thing to know before reading the fixtures

With a mocked repository, nothing is ever persisted, so **Hibernate
never runs**. `@GeneratedValue` leaves ids null and `@CreationTimestamp`
leaves `submittedAt` and `createdAt` null — and the service mappers call
`.toString()` on those. `TestFixtures` and the `stubSaveAsIfPersisted`
helper assign them by hand, standing in for what Hibernate does during
`persist()`.

This is not a test artifact. It is the same mechanism behind a real bug
in development: `ReferralStatusHistory.createdAt` was a
`@CreationTimestamp`, but the row is persisted by **cascade**, which
happens at flush rather than at `save()`. `ReferralService.decide()`
maps its response while the transaction is still open, so the field was
still null and the mapping threw an NPE. The fix was to assign
`createdAt` in the constructor — see the comment on that field.

---

## Running a subset

```bash
mvn test -Dtest=ReferralServiceTest              # one class
mvn test -Dtest='*ServiceTest'                   # the service layer
mvn test -Dtest=ReferralServiceTest'$Decide'     # one @Nested group
```

## If a controller test fails after an upgrade

The controller tests depend on two argument resolvers registered by hand
in `ControllerTestSupport`: `PageableHandlerMethodArgumentResolver` for
`Pageable`, and `PrincipalResolver` for `UserPrincipal`. If a Spring
upgrade changes resolver ordering, those are the first place to look —
the symptom is a 400 or a null principal on an endpoint that works fine
in the running application.
