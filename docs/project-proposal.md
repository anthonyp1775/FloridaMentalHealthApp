# Project Proposal — Florida Mental Health App

**Course:** UCI 2123 — Systems Engineering with AWS
**Project:** Capstone (Custom Project)
**Author:** Anthony Pino
**Date:** October 4, 2026

---

## 1. Problem Statement

Finding mental health care in Florida is difficult for reasons that have
little to do with clinical need and a great deal to do with information.

A person who has decided to seek help typically starts with whatever
directory their insurance company publishes. Those directories are
notoriously stale: listings persist for clinicians who have moved,
retired, or stopped taking that plan. They almost never indicate whether
anyone is currently accepting new clients. Language capability is rarely
a searchable field, which matters enormously in a state where Spanish and
Haitian Creole are primary languages for large populations. And the
distinction between credentials — who can prescribe medication and who
cannot — is usually buried in an acronym the reader has no reason to
recognize.

The result is a sequence of phone calls, each one requiring the person to
re-explain their situation, most of them ending in "we're not taking new
patients" or "we don't take that insurance." Every call is a point at
which someone who has already overcome the hardest step — deciding to
ask for help — can reasonably give up.

The gap is not a shortage of information. It is that the information is
scattered, unverified, and organized around the payer rather than the
person.

## 2. Business Justification

**For the person seeking care:** fewer dead-end calls, and filters built
on things they can actually state about themselves — their county, their
insurance, their preferred language, the kind of support they are
looking for, whether they can travel.

**For clinics and navigators:** a structured intake queue instead of
voicemail. A referral arrives with the relevant details already
attached, is reviewed once, and resolves to a clear outcome with an
audit trail. Capacity is a maintained field rather than something a
receptionist recites from memory.

**For the system as a whole:** the data this application collects
surfaces the access gap directly. A report showing how many
currently-accepting providers take Medicaid versus commercial insurance
is the kind of number that informs where resources should go — and it
falls out of the referral workflow rather than requiring a separate
survey.

**The scale of it.** As of 31 December 2025, Florida had **239
designated Mental Health Care Health Professional Shortage Areas**,
covering a population of **9.87 million people**. Across those areas
only about **25% of need is met**, and HRSA estimates that **545
additional practitioners** would be required to lift the designations
entirely.

This application does not add practitioners, and it would be dishonest
to suggest otherwise. What it reduces is the number of calls a person
makes before reaching one of the clinicians who already exist — which is
the part of this problem that is an information problem rather than a
workforce problem.

> Source: Bureau of Health Workforce, Health Resources and Services
> Administration (HRSA), U.S. Department of Health & Human Services,
> *Designated Health Professional Shortage Areas Statistics: Designated
> HPSA Quarterly Summary*, as of 31 December 2025, via KFF State Health
> Facts. <https://data.hrsa.gov/topics/health-workforce/shortage-areas>

## 3. Target Users

| Role | Who they are | What they need |
|---|---|---|
| **ROLE_USER** | A person in Florida seeking mental health care for themselves | To find providers who match their real constraints, and to get a referral moving without a phone tree |
| **ROLE_ADMIN** | A care navigator or clinic intake staff member | To work a referral queue efficiently and keep the directory accurate |

Both roles use the same application with substantially different
surfaces — not the same screens with buttons hidden.

## 4. User Stories

### Person seeking care (ROLE_USER)

1. As a person seeking care, I want to filter providers by county,
   insurance, language, and area of focus, so that the results are
   limited to people I could actually see.

2. As a person seeking care, I want to see whether a provider is
   accepting new clients before I contact them, so that I don't spend a
   week waiting on a callback from a full caseload.

3. As a person seeking care, I want to know which providers can
   prescribe medication, so that I reach the right kind of clinician the
   first time instead of decoding credential acronyms.

4. As a person seeking care, I want to filter for telehealth, so that I
   can find care without arranging transportation or time off work.

5. As a person seeking care, I want to save providers to a shortlist, so
   that I can compare options without starting my search over.

6. As a person seeking care, I want to submit a referral request through
   the app, so that I don't have to explain my situation over the phone
   to a stranger.

7. As a person seeking care, I want to see the current status of my
   request, so that I know whether anyone has looked at it yet.

8. As a person seeking care, I want to withdraw a request I no longer
   need, so that I'm not holding a slot someone else could use.

9. As a person seeking care, I want crisis resources visible on every
   screen, so that I can find immediate help if a directory is not what
   I need right now.

### Navigator / clinic staff (ROLE_ADMIN)

10. As a navigator, I want a queue of pending referrals ordered
    oldest-first, so that nobody falls through the cracks.

11. As a navigator, I want to accept or waitlist a request in a single
    action, so that reviewing a referral takes seconds rather than
    minutes.

12. As a navigator, I want to see the full history of a referral,
    including who changed what and when, so that I can answer a client's
    question about their case.

13. As a navigator, I want to update a provider's open slots and
    accepting status, so that the directory reflects actual capacity
    rather than last month's.

14. As an administrator, I want a report of how many accepting providers
    take each insurance type, so that I can show where the access gaps
    are.

15. As an administrator, I want to add and edit organizations and
    providers, so that the directory can grow without a developer.

## 5. Functional Requirements

| # | Requirement |
|---|---|
| FR-1 | Users can register with first name, last name, email and password. Email must be unique. Passwords are stored only as BCrypt hashes. |
| FR-2 | Users can log in and receive a JWT. All endpoints except registration, login, API docs, and the public resources page require a valid token. |
| FR-3 | The system enforces two roles, ROLE_USER and ROLE_ADMIN, with distinct permitted operations. |
| FR-4 | Users can search providers by any combination of county, clinical focus, language, insurance plan, population served, and telehealth availability, and can restrict results to providers currently accepting new clients. |
| FR-5 | Search results are paginated. |
| FR-6 | Users can view a provider's full detail, including credential, organization, county, languages, specialties, populations served, accepted insurance, open slots, and typical wait. |
| FR-7 | Users can add providers to and remove them from a personal shortlist. |
| FR-8 | Users can submit a referral request to a provider with an optional free-text message and a preferred contact method. The system rejects a second open request to the same provider. |
| FR-9 | Users can view their own referrals and their status, and can withdraw a pending request. |
| FR-10 | ROLE_ADMIN can view a queue of pending referrals ordered oldest-first. |
| FR-11 | ROLE_ADMIN can resolve a referral to ACCEPTED, WAITLISTED or DECLINED with an optional note. Accepting decrements the provider's open slots; waitlisting increments the waitlist count. If no capacity remains at the moment of review, the request resolves to WAITLISTED rather than overbooking. |
| FR-12 | Every referral status change writes an immutable history row recording the previous status, the new status, the actor, and a timestamp, within the same transaction as the change. |
| FR-13 | ROLE_ADMIN can create and update organizations and providers, and can adjust provider capacity. |
| FR-14 | Provider deletion is a soft delete; records referenced by referral history are never removed. |
| FR-15 | ROLE_ADMIN can view reports covering referral volume by status and date range, accepting-provider counts by insurance plan type, and capacity by county. |
| FR-16 | Users can maintain a profile of stated preferences (county, language, insurance, telehealth preference, contact preference) used to pre-fill search filters. |
| FR-17 | Crisis resources are displayed persistently on every screen, and a statewide resources page is reachable without authentication. |
| FR-18 | All request payloads are validated server-side; validation failures return field-level errors. |
| FR-19 | All errors return a single consistent JSON shape containing timestamp, status, error, message and path. |

## 6. Non-Functional Requirements

### Performance
- **NFR-1** — Provider search returns within 500 ms for the seeded
  dataset. All foreign keys and filter columns are indexed.
- **NFR-2** — List endpoints are paginated; no endpoint returns an
  unbounded collection.
- **NFR-3** — All to-one JPA relationships use lazy fetching, and
  `spring.jpa.open-in-view` is disabled, so no query is issued outside
  the service layer.

### Security
- **NFR-4** — Passwords are hashed with BCrypt at strength 11. Plaintext
  passwords are never stored, logged, or returned.
- **NFR-5** — Authentication is stateless JWT; no server-side session is
  created.
- **NFR-6** — Secrets (JWT signing key, database credentials) are read
  from environment variables in the production profile and never
  committed to source control.
- **NFR-7** — Authorization is enforced server-side on every endpoint.
  A user can read or modify only their own profile, shortlist, and
  referrals; the authenticated identity is taken from the security
  context, never from a request parameter.
- **NFR-8** — API responses are DTOs, never JPA entities, so password
  hashes and internal relationships cannot leak.
- **NFR-9** — CORS is restricted to the known frontend origin.
- **NFR-10** — Database integrity is enforced by constraints (foreign
  keys, unique constraints, check constraints) rather than by
  application code alone.

### Reliability
- **NFR-11** — Referral resolution is transactional. A failure at any
  step leaves capacity, status, and audit trail unchanged.
- **NFR-12** — The schema and seed scripts are idempotent and may be
  re-run to return the database to a known state.

### Maintainability
- **NFR-13** — The codebase follows a layered architecture
  (controller → service → repository → entity) with constructor
  injection throughout.
- **NFR-14** — Service-layer unit test coverage of at least 70%,
  covering both success and failure paths.
- **NFR-15** — API documentation is generated from the code via OpenAPI
  rather than maintained by hand.

### Accessibility and usability
- **NFR-16** — The interface is responsive across phone, tablet and
  desktop. Given the audience, phone is the primary target.
- **NFR-17** — Interactive elements carry ARIA labels and are reachable
  by keyboard. Information is never conveyed by color alone.
- **NFR-18** — Asynchronous operations show explicit loading and error
  states.

### Scalability
- **NFR-19** — The API is stateless, so additional instances can be
  added behind a load balancer without session affinity.
- **NFR-20** — Search filtering and pagination are performed in the
  database, not in application memory.

## 7. Out of Scope

These exclusions are deliberate design decisions, documented here rather
than discovered later.

**No clinical data.** The system stores contact details and stated
preferences only. No diagnoses, assessments, clinical notes, or
treatment history are stored anywhere in the schema. This keeps the
application entirely outside the handling of protected health
information.

**No screening or triage logic.** The obvious feature for an application
in this space is an intake questionnaire that scores symptom severity
and routes accordingly. It is excluded on purpose: it would imitate a
clinical instrument without clinical validation or oversight, and
storing its output would make every record PHI. Search filters on
self-described preferences instead, which is both safer and — because
the useful filters were never the clinical ones — no less effective.

**Not an emergency service.** The application cannot help someone in an
acute crisis, and does not present itself as able to. Crisis resources
are displayed persistently and the limitation is stated plainly in the
interface.

**Directory data is synthetic.** Every seeded organization name is
prefixed "Example". Because the schema stores open slots, waitlist
counts, acceptance status and insurance participation, attaching
invented values to a real clinic's name would constitute a fabricated
record about a real organization. The statewide resources page is
separately maintained with genuinely accurate information.

**Florida only.** County and managing-entity reference data is
Florida-specific. Multi-state support would require generalizing the
geographic model.

**No provider self-service.** Providers do not have accounts and cannot
update their own listings; a navigator maintains the directory. A
self-service portal is the single highest-value future addition, since
it would address the staleness problem at its source.

**No appointment scheduling.** The system produces a referral, not a
booked appointment. Scheduling happens between the clinic and the client
outside this application.

**No messaging.** There is no in-app chat between clients and providers.
The referral carries a preferred contact method and contact happens
through that channel.

**No AWS deployment.** Phase 5 of the course prompt (EC2, RDS, S3,
CodePipeline, CloudWatch) is excluded from this build.

### Future scope

Ranked by value, were the project to continue:

1. Provider self-service portal for capacity and listing updates
2. Treatment-approach filter (CBT, DBT, EMDR, ACT, ERP)
3. Level-of-care filter (outpatient, IOP, PHP, residential)
4. Email and SMS notification on referral status change
5. Multi-state expansion
6. Integration with a live provider data source to reduce staleness

---

## Appendix — Traceability

Each user story maps to at least one functional requirement, and each
functional requirement maps to implemented endpoints and tables.

| Story | Requirements | Primary tables |
|---|---|---|
| 1, 3, 4 | FR-4, FR-5 | `providers`, `organizations`, `counties`, `provider_*` join tables |
| 2 | FR-4, FR-6 | `providers` |
| 5 | FR-7 | `saved_providers` |
| 6, 8 | FR-8, FR-9 | `referral_requests`, `referral_status_history` |
| 7, 12 | FR-9, FR-12 | `referral_status_history` |
| 9 | FR-17 | _(frontend only — no persistence)_ |
| 10, 11 | FR-10, FR-11 | `referral_requests`, `providers` |
| 13, 15 | FR-13, FR-14 | `providers`, `organizations` |
| 14 | FR-15 | `provider_insurance`, `insurance_plans` |
