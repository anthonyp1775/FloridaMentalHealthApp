# Demo Runbook — Florida Mental Health App

A rehearsed path through the application. **Run it end to end the night
before, then again an hour before you present.**

The order matters: it follows one person's actual journey, so each step
sets up the next. Roughly 12 minutes without questions.

---

## Before you start

**Running:**

- MySQL, with `fl_mental_health_db` freshly seeded
- Backend: run `FloridaMentalHealthApplication` — wait for
  `Started FloridaMentalHealthApplication in …`
- Frontend: `cd frontend && npm run dev` — open `http://localhost:5173`

**Open in tabs, ready to switch to:**

| Tab | What it's for |
|---|---|
| The app | The demo itself |
| MySQL Workbench | The audit-trail proof in section 6 |
| IntelliJ, test results visible | The test run — 258 cases |
| `target/site/jacoco/index.html` | Coverage |

Re-run the query below
once to confirm the navigator still holds ROLE_ADMIN.

```sql
SELECT u.id, u.email, GROUP_CONCAT(r.name ORDER BY r.name) AS roles
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles      r  ON r.id = ur.role_id
GROUP BY u.id, u.email ORDER BY u.id;
```

| | Value |
|---|---|
| Client email | `alicia.moreno@example.com` |
| Navigator email | `navigator@carepathfl.org` |
| Password (both) | `Password123` |
| Provider WITH open slots | `________________________` |
| Provider at ZERO slots | `________________________` |

> The navigator must show **`ROLE_ADMIN,ROLE_USER`**. If it shows only
> `ROLE_USER`, fix `user_roles` and log in again — a token carries the
> roles it was issued with, so a database change does not update a
> session already signed in.

Seeded providers at zero capacity: Priya Raman, Gregory Lindt, Camille
Beaumont, Leah Kirkpatrick, Hannah Wexler, Wendell Frayne, Lydia Alvarez.

---

## 1. Open on the problem, not the architecture — 45 seconds

Land on the login screen. Don't sign in yet.

> "Finding a mental health provider in Florida means calling a list of
> numbers and asking each one the same question: are you taking new
> clients, and do you take my insurance. Most directories can't answer
> either. This one is built around that question."

Point at the dark bar across the top.

> "That's on every screen, including this one — before you have an
> account. A directory can't help someone in an acute crisis, and this
> app doesn't pretend otherwise."

Click **Go to resources**.

> "This page is public on purpose. Every provider in the directory is
> sample data — every organization name starts with 'Example'. These
> helplines are real. Someone who opens this app and actually needs help
> should find something true."

Go back, sign in as the client.

---

## 2. Search — the core experience — 2 minutes

The search page opens straight into filters and results. No marketing
page.

> "This is the whole product. No landing page — it's a tool."

Point at the **colored left edge** on the cards.

> "Green means accepting with openings. Amber is waitlist only. Grey is
> closed. That's the fact every other directory buries, so here it gets
> structural weight instead of a badge in a corner — and it's always
> paired with text, never color alone, because about one in twelve men
> can't reliably tell those two colors apart."

Narrow the search, one filter at a time:

1. County → **Miami-Dade**
2. Focus area → **PTSD & Trauma**
3. Language → **Spanish**
4. Tick **Only show providers accepting new clients**

> "Every filter is optional and they compose. One query handles all 128
> combinations — a null parameter disables its clause rather than
> needing a separate method per combination."

Open a provider.

> "Credential is spelled out, not abbreviated. It's what determines who
> can prescribe medication, and it's the single thing people most often
> get wrong before they book."

**Save** them, then show the **Saved** tab.

> "There's no user id anywhere in that request. It comes from the token.
> That's why nobody can read or change someone else's list by changing a
> number in the URL."

---

## 3. Send a referral — 1 minute

From the provider page, send a request.

> "Nothing here asks a clinical question. The message box is free text
> and the placeholder asks about scheduling, not symptoms. The app never
> holds health information, so it never has to protect any — that's a
> documented decision, ADR-0004."

Go to **My requests** → expand the history.

> "One row already: created, attributed to me, timestamped."

---

## 4. The navigator side — 1 minute

Sign out. Sign in as the navigator. Three new links appear.

> "Same application, same controller. The difference is the roles in the
> token."

Open **Queue**.

> "Oldest first, deliberately. A newest-first queue guarantees that the
> longest-waiting request is the one nobody ever sees."

---

## 5. THE MOMENT — accept a referral that can't be accepted — 3 minutes

**Rehearse this one.** It's the technical core of the project.

First, submit a referral to a provider with **zero open slots** (do this
as the client beforehand, or use a seeded one). Then, as the navigator,
open it in the queue and click **Accept**.

The amber notice appears: *you asked for Accepted, but the provider had
no open slots at review time, so the request was set to Waitlisted.*

Say this slowly:

> "I asked for ACCEPTED. The system gave me WAITLISTED.
>
> The provider's last slot was gone, so accepting would have promised an
> intake that doesn't exist.
>
> The whole method is one transaction. Inside it, the provider row is
> re-read with a pessimistic write lock — `SELECT … FOR UPDATE`. Without
> that lock, two navigators accepting at the same moment could both read
> one open slot and both decrement it.
>
> Four things then happen together or not at all: capacity adjusts, the
> status changes, resolvedAt and resolvedBy are set, and a history row is
> written. If any step fails, all of it rolls back — no half-decremented
> capacity, and no status change without its audit row.
>
> And the response reports what actually happened, not what was asked
> for. Silently doing something different from what the caller requested
> would be worse than failing."

Open the referral's history to show both rows, including the reason.

**If asked "how do you know the lock works?"** — the lock is SQL
behaviour, confirmed by reading the generated statement. What the test
suite proves is that the service *asks for* the locking read on exactly
that path rather than reusing the stale copy hanging off the referral —
`ReferralServiceTest.decide_usesPessimisticReadForCapacity`. That's the
part a refactor would quietly break.

---

## 6. The audit trail, proved in SQL — 1 minute

Switch to Workbench.

```sql
SELECT r.id, r.status, h.to_status AS latest_history_status
FROM referral_requests r
LEFT JOIN referral_status_history h
       ON h.id = (SELECT MAX(h2.id) FROM referral_status_history h2
                  WHERE h2.referral_request_id = r.id)
WHERE h.id IS NULL OR h.to_status <> r.status;
```

**Zero rows.**

> "Every referral's current status matches the latest entry in its
> history. No referral changes status without a row recording who moved
> it, from what, to what, and when — and the status change and the audit
> row are written in the same transaction, so one can't happen without
> the other."

---

## 7. The invariant that keeps the directory honest — 45 seconds

Back in the app: **Directory** → pick anyone → **Edit capacity** → set
open slots to **0**, leave **Accepting new clients** ticked → Save.

The error comes back: *A provider cannot be accepting new clients with
zero open slots.*

> "A directory that says someone is available when they're not is worse
> than no directory. That rule is enforced on every write path, not just
> this one — and the message you're seeing is the backend's, not a copy
> of the rule re-implemented in the browser. One rule, one place."

---

## 8. The report that justifies the whole thing — 1 minute

Open **Reports**. Point at the access-gap bars.

> "This is the number the system exists to produce. It isn't a count of
> rows — it's how many clinicians *currently accepting clients* take
> each kind of coverage. And it falls out of data the directory already
> holds. No separate survey, no extra form for anyone to fill in.
>
> I want to be straight about what you're looking at, though. In the
> real world the finding is usually that fewer clinicians take Medicaid
> than take commercial insurance. My seed data doesn't show that —
> Medicaid is actually ahead of commercial here, because I wrote 35
> providers to exercise the query, not to model the shortage. So this
> chart demonstrates the mechanism, not the finding. Point it at real
> intake data and it reports whatever is actually true."

Then move to the county table, which *does* tell a story:

> "Compare two rows. Miami-Dade has the most providers of any county —
> seven — and still has more people waiting than it has open slots: 33
> against 27. Now Hillsborough: four providers, six open slots, thirty
> people waiting. Five waiting for every opening.
>
> Those two rows say different things. Miami-Dade is stretched.
> Hillsborough is a bottleneck. A single statewide provider count would
> show neither, and a navigator deciding where to send someone this
> afternoon needs exactly this distinction."

**If asked why you didn't just make the Medicaid number lower:** because
then the chart would be reporting my assumption instead of the data.
Showing the mechanism honestly is worth more than a chart that agrees
with my proposal.

---

## 9. How it's verified — 1 minute

Switch to IntelliJ.

> "186 test methods across 13 classes, 258 cases executed — several are
> parameterized, so the entity identity contract alone runs 66. Services
> with mocked repositories,
> controllers through MockMvc, entities with no mocks at all, and the
> JWT service cutting real tokens and then trying to break them —
> tampered, expired, signed with a different key."

Switch to the JaCoCo report.

> "80.4% line coverage, on 964 lines. The uncovered remainder is mostly
> `config` — Spring startup wiring that only runs when the app boots."

Mention the boundary honestly:

> "Role enforcement isn't covered by the JUnit tests, because
> `@PreAuthorize` needs a Spring proxy that the standalone MockMvc setup
> doesn't build. It's verified end to end instead, in the 39-request API
> collection, against the real filter chain and real tokens. That's the
> right level for it."

---

## 10. Close on scope — 30 seconds

> "What this deliberately doesn't do: no screening questionnaires, no
> diagnoses, no clinical records. It filters on what a person tells you
> they want — county, insurance, language, focus area, modality — and
> nothing else. That keeps the whole application outside PHI handling,
> which is a far smaller promise to keep than securing it would be."

---

## If something breaks mid-demo

**Say it out loud.** "That's a stale token, give me five seconds" lands
far better than silent clicking.

| Symptom | Cause | Fix |
|---|---|---|
| Every screen: "Cannot reach the server" | Backend not running | Start it; wait for `Started …Application` |
| 401 everywhere, suddenly | Token expired (24h) | Sign out, sign in |
| Admin links missing | Signed in as the client | Sign out, sign in as navigator |
| Admin links present but 403 | Token predates the role fix | Sign out, sign in |
| 409 on submit | That referral exists from a previous run | Different provider, or re-seed |
| Accept didn't waitlist | That provider still has slots | Use one from the zero-capacity list |
| A number looks wrong | Previous rehearsal mutated data | Re-seed |

## Resetting between rehearsals

Re-run `seed.sql` whole. It truncates and reloads, so capacity and
referrals return to their starting values. Then:

```sql
SELECT (SELECT COUNT(*) FROM counties)          AS counties,
       (SELECT COUNT(*) FROM organizations)     AS organizations,
       (SELECT COUNT(*) FROM providers)         AS providers,
       (SELECT COUNT(*) FROM referral_requests) AS referrals;
```

Expect **67 / 17 / 35 / 10**.

---

## The three questions you are most likely to get

**"Why not use real clinics?"**
The schema stores open slots, waitlist counts, acceptance status and
insurance participation. Attaching invented values to a real
organization would be a fabricated record about a real place — someone
could call it. Every organization is prefixed "Example". The statewide
resources page is the exception, and it's real.

**"Why is there no screening or triage?"**
ADR-0004. Screening means holding clinical information, which means
PHI, which means a security and compliance burden a course project
can't honestly meet. Filtering on self-described preferences answers
the question people actually have — who can see me, and will they take
my insurance — without ever asking a clinical one.

**"What would you do next?"**
Capacity goes stale the moment a provider's schedule changes, and
nothing in this system asks them. The next thing worth building is a
provider-facing way to update their own availability — everything else
is downstream of that number being true.
