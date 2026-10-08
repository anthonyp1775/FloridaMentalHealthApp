# Bruno Collection — Florida Mental Health API

37 requests with assertions, covering every endpoint plus the failure
paths that matter.

## Opening it

1. Open Bruno → **Open Collection** → select this `bruno` folder.
2. Top-right environment dropdown → choose **Local**.
3. Set the `password` variable to your demo password.

## Running it

**Run the whole collection**: right-click the collection → *Run*.
Folders execute in numbered order and requests in `seq` order, which
matters — tokens and ids are captured by earlier requests and used by
later ones.

**Run one folder**: right-click it → *Run*. Note that folders 02–06
need tokens, so run **01 Auth** first in a fresh session.

## How the chaining works

Nothing is hardcoded. Each request stashes what the next one needs:

| Captured by | Variable | Used by |
|---|---|---|
| Login as navigator | `adminToken` | every ADMIN request |
| Login as client | `clientToken` | every USER request |
| Counties | `countyMiamiDade` | the multi-filter search |
| Insurance plans | `planMedicaid` | the multi-filter search |
| Search all | `providerWithCapacity`, `providerAtCapacity` | the referral tests |
| Provider detail | `slotsBefore` | "Capacity was consumed" |
| Submit referral | `referralId` | accept / duplicate / already-resolved |
| Submit to a full provider | `fullReferralId` | "Accept becomes waitlist" |

That means the suite survives re-seeding, different id ordering, or a
different machine.

## What it proves

**01 Auth** — login issues a JWT carrying the right roles; a bad
password returns an identical message to an unknown account, so the API
cannot be used to enumerate which emails are registered; validation
failures name every offending field.

**02 Catalog** — all 67 counties with region and managing entity;
Medicaid, commercial and sliding-scale plan types present. Also asserts
that `/api/specialties` now returns 404: Specialty, Language, Population
and ClientProfile were removed when the data model went from 18 tables
to 11, and this fails if any of them come back as a dead route.

**03 Providers** — 35 providers, paginated; each filter narrows the
result; no duplicate rows from the insurance join; organisation and
county arrive flattened to strings; the removed collections are absent
from the detail response; a provider cannot be marked as accepting with
zero open slots.

**04 Referrals** — the full workflow, including the three cases worth
demonstrating:
- a second open request to the same provider is refused (409), which is
  the rule MySQL cannot express as a partial unique index
- accepting consumes exactly one slot, and writes a history row
- **accepting a full provider returns WAITLISTED**, with the response
  reporting what actually happened rather than what was requested

**05 Reports** — the access gap by plan type, capacity by county, and
referral volume by status.

**06 Security** — no token is 401 and not 403; a tampered token is
rejected; an unknown path is 404 and not 500; the wrong HTTP method is
405 and not 500. The last two are regression tests — both were real
bugs, caused by a catch-all exception handler swallowing framework
exceptions that already carried a correct status.

## Re-running

"Save to shortlist" is idempotent-tolerant — it accepts either 201 or
409, so a second run passes.

The referral requests are not — a submitted referral stays submitted.
For a clean run, re-execute `seed.sql` first.

## A note on the deliverable

The course submission guidelines name a **Postman** collection exported
as JSON. Bruno was approved as a substitute by the instructor.

The reason for preferring it: Bruno stores each request as a plain text
file, so a change to an assertion shows up as a readable line in a diff
and gets reviewed like code. A Postman collection is a single large JSON
blob where the same change is a one-line edit buried in an unreadable
diff. For a repository where the test suite is part of what is being
assessed, that difference matters.

Every request also carries its assertions inline, so the file is both
the request and its contract.
