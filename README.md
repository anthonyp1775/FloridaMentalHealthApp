# Florida Mental Health App

A mental health navigation and referral system for the state of Florida.

UCI 2123 — Systems Engineering with AWS — Capstone Project.

---

## Problem

Finding mental health care in Florida is hard for reasons that have
nothing to do with clinical need. Provider directories are stale, they
rarely say whether anyone is actually accepting new clients, insurance
participation is unclear until you call, and language access is almost
never a searchable field. People give up somewhere in that gap.

This system lets a person filter on the things they can actually state
about themselves — county, insurance, language, area of focus,
telehealth or in person — and see who has real intake capacity right
now. They can shortlist providers, submit a referral request, and track
its status. Navigator and clinic staff work the other side: reviewing
the queue, accepting or waitlisting, and managing the directory.

## Scope boundaries

These are deliberate, not omissions:

- **No clinical data.** The system stores contact information and
  stated preferences. No diagnoses, assessments, clinical notes, or
  treatment history — anywhere.
- **No screening instruments.** Search filters on self-described
  preferences, never on symptom severity. This is a directory, not a
  triage tool.
- **Not for emergencies.** Crisis resources (988 Suicide & Crisis
  Lifeline, Crisis Text Line, 911) are rendered persistently on every
  screen, including before login. A directory cannot help someone in an
  acute crisis and the app does not pretend otherwise.
- **The directory is synthetic; the resources are real.** Every
  organization name is prefixed "Example". This database stores
  `open_slots`, `waitlist_count`, acceptance status and insurance
  participation — attaching invented values for those to a real clinic's
  name would be a fabricated record about a real place, so the prefix
  makes the sample nature unmistakable. Separately, `/resources` is a
  public page of genuinely accurate statewide resources (988, Crisis
  Text Line, 211 Florida, SAMHSA, NAMI Florida), reachable without
  logging in. County names are real; region and managing-entity
  groupings are approximate and flagged for verification in `seed.sql`.
- **No AWS deployment.** Phase 5 of the course prompt is out of scope
  for this build.

## Stack

**Languages** — Java 25, JavaScript (ES2020+/JSX), SQL, HTML, CSS

**Backend** — Spring Boot 3.5.16, Spring Web, Spring Data JPA,
Hibernate 6.6, Spring Security 6.5, jjwt 0.12.6, Jakarta Bean
Validation, springdoc-openapi 2.8.6, HikariCP, Maven

**Frontend** — React 18, Vite, React Router 6, Axios

**Database** — MySQL 8

**Testing** — JUnit 5, Mockito, AssertJ, MockMvc, H2, JaCoCo, Postman,
SonarQube

In one line: a React single-page app talking over REST to a Spring Boot
API, backed by MySQL, secured with stateless JWT authentication.

---

## Running locally

### 1. Database

Open `backend/src/main/resources/db/schema.sql` in MySQL Workbench and
run it. Then generate a BCrypt hash, paste it over every
`PASTE_BCRYPT_HASH_HERE` in `seed.sql`, and run that too.

Verification queries are commented at the bottom of both files. Queries
1 and 2 in each should return **zero rows**.

### 2. Backend

```bash
cd backend
# set your MySQL password in src/main/resources/application-dev.properties
./mvnw spring-boot:run
```

Runs on http://localhost:8080 — API docs at `/swagger-ui.html`

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on http://localhost:5173, proxying `/api` to the backend.

### Tests and coverage

```bash
cd backend
./mvnw clean test
# report: target/site/jacoco/index.html
```

---

## Data model

18 tables, 13 of which have entity classes — the five pure join tables
are handled by `@ManyToMany` and need none.

**Security** — `roles`, `users`, `user_roles`, `client_profiles`

**Reference** — `counties` (all 67), `specialties`, `populations`,
`languages`, `insurance_plans`

**Directory** — `organizations`, `providers`, plus the four join tables
`provider_specialties`, `provider_populations`, `provider_languages`,
`provider_insurance`

**Workflow** — `referral_requests`, `referral_status_history`,
`saved_providers`

### Design notes

**County lives on `organizations`, not `providers`.** A provider works
at a clinic; the clinic has the address. Location search joins
provider → organization → county.

**Capacity is the moving part.** `providers.open_slots` decrements
inside `ReferralService.decide()`, which is `@Transactional`. If the
last slot filled between submission and review, the request resolves to
WAITLISTED instead — never overbooked, never silently failed.

**Nothing changes status without an audit row.** Every transition
writes to `referral_status_history` with the actor and a note, inside
the same transaction as the change itself.

**`client_profiles` uses a shared primary key.** Its PK is also its FK
to `users` (`@MapsId`), which makes a duplicate profile structurally
impossible rather than merely constrained.

**One constraint is enforced in code, on purpose.** "Only one PENDING
request per user/provider pair" cannot be expressed in MySQL — there is
no partial unique index — so `ReferralService` enforces it. Documented
rather than pretended.

---

## Roles

| | ROLE_USER | ROLE_ADMIN |
|---|---|---|
| Search providers | yes | yes |
| Save a shortlist | yes | yes |
| Submit / withdraw a referral | yes | yes |
| Own profile | yes | yes |
| Referral queue and decisions | — | yes |
| Manage directory and capacity | — | yes |
| Reports | — | yes |

---

## Build order

| Day | Work |
|---|---|
| 1 | Proposal, architecture diagram, ERD, API design |
| 2 | `schema.sql` + `seed.sql`, entities, repositories |
| 3 | DTOs, services, controllers, exception handler |
| 4 | React app, routing, API integration |
| 5 | JWT security, role rules, tests to 70%+ |
| 6 | *(AWS deployment — out of scope)* |
| 7 | Docs, presentation, peer review |
