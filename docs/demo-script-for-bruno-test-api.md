# Demo Runbook — Florida Mental Health App

A rehearsed path through the API. Run it end to end before the
presentation, then again on the day.

**Before you start**
- App running on `http://localhost:8080`
- Database seeded (67 counties, 17 organizations, 35 providers)
- Two Postman environment variables: `clientToken`, `adminToken`

**Fill in your own ids.** Everything below marked `<...>` comes from a
previous response. Record them in the blanks as you go.

| What | Value |
|---|---|
| Demo password | `________________` |
| Provider WITH capacity | id `____` name `________________` |
| Provider at ZERO capacity | id `____` name `________________` |
| Referral id (with capacity) | `____` |
| Referral id (no capacity) | `____` |

---

## 0. Get both tokens

### 0a. Client

```
POST /api/auth/login
Content-Type: application/json

{ "email": "alicia.moreno@example.com", "password": "<password>" }
```

**200.** Save `token` as `clientToken`. Roles: `["ROLE_USER"]`.

### 0b. Navigator

```
POST /api/auth/login

{ "email": "navigator@carepathfl.org", "password": "<password>" }
```

**200.** Save `token` as `adminToken`. Roles must include `ROLE_ADMIN`.

> If the navigator shows only `ROLE_USER`, the role assignment is
> missing — fix it in `user_roles` and log in again. A token carries
> the roles it was issued with; a database change does not update it.

---

## 1. Authentication works, and failures are honest

### 1a. Wrong password

```
POST /api/auth/login

{ "email": "alicia.moreno@example.com", "password": "wrong" }
```

**401** — "Email or password is incorrect"

> Say: the message is identical whether the email exists or not.
> Telling the two apart would let someone enumerate which addresses
> have accounts.

### 1b. No token at all

```
GET /api/counties
```
(no Authorization header)

**401** — "Authentication required - provide a valid bearer token"

---

## 2. Reference data — the search filters

```
GET /api/counties
Authorization: Bearer {{clientToken}}
```

**200** — 67 counties, each with `region` and `managingEntity`.

> Say: all 67 Florida counties, grouped by DCF region and tagged with
> the Managing Entity that administers state-funded behavioral health
> there. That regional structure is real and specific to Florida.

Also worth showing quickly:

```
GET /api/specialties          → 20 clinical focus areas
GET /api/languages            → includes Spanish and Haitian Creole
GET /api/insurance-plans      → Medicaid MCOs, commercial, sliding scale
```

**Record ids you will need:**

| Lookup | Name | id |
|---|---|---|
| Specialty | PTSD & Trauma | `____` |
| Language | Spanish | `____` |
| County | Miami-Dade | `____` |

---

## 3. Search — the core experience

### 3a. Everything

```
GET /api/providers/search
Authorization: Bearer {{clientToken}}
```

**200** — 35 providers, paged 20 at a time.

### 3b. Narrow it, one filter at a time

```
GET /api/providers/search?acceptingOnly=true
GET /api/providers/search?acceptingOnly=true&telehealth=true
GET /api/providers/search?acceptingOnly=true&telehealth=true&languageId=<spanish>
GET /api/providers/search?acceptingOnly=true&telehealth=true&languageId=<spanish>&specialtyId=<ptsd>
```

Each returns fewer than the last.

> Say: every filter is optional and they compose. One query handles all
> 128 combinations — a null parameter disables its clause rather than
> needing a separate method per combination.

### 3c. Full detail

```
GET /api/providers/<id>
```

**200** — credential, organization, county, and all four collections as
name arrays.

> Say: credential matters more than it looks. It determines who can
> prescribe medication, which is the thing people most often get wrong
> when they book an appointment.

**Record:** a provider with `openSlots > 0` and one with `openSlots: 0`
(seeded: Priya Raman, Gregory Lindt, Camille Beaumont, Leah
Kirkpatrick, Hannah Wexler, Wendell Frayne, Lydia Alvarez).

---

## 4. Shortlist — identity comes from the token

```
POST /api/providers/<id>/save
Authorization: Bearer {{clientToken}}

{ "note": "Speaks Spanish, close to work" }
```

**201**

```
GET /api/providers/saved
Authorization: Bearer {{clientToken}}
```

**200** — just that user's list.

> Say: there is no user id anywhere in these requests. It comes from
> the token. That is why no one can read or change someone else's list
> by changing a number in the URL.

---

## 5. Referral — the happy path

### 5a. Submit

```
POST /api/referrals
Authorization: Bearer {{clientToken}}

{
  "providerId": <provider WITH capacity>,
  "message": "Looking for weekday evenings if possible",
  "preferredContact": "EMAIL"
}
```

**201** — status `PENDING`, one history row (`null → PENDING`,
attributed to Alicia).

**Record the referral `id`.**

### 5b. Duplicate is rejected

Send the exact same request again.

**409** — "You already have a pending request with this provider"

> Say: MySQL cannot express "unique only when status is PENDING" —
> there is no partial unique index — so the service enforces it. That
> is documented as a deliberate choice rather than pretended at the
> schema level.

### 5c. The queue

```
GET /api/referrals/queue
Authorization: Bearer {{adminToken}}
```

**200** — pending referrals, **oldest first**.

> Say: oldest first on purpose. A newest-first queue guarantees that
> the longest-waiting request is the one nobody sees.

### 5d. A client cannot resolve referrals

```
POST /api/referrals/<id>/decision
Authorization: Bearer {{clientToken}}

{ "status": "ACCEPTED", "note": "..." }
```

**403** — "You do not have access to this resource"

### 5e. Accept it

```
POST /api/referrals/<id>/decision
Authorization: Bearer {{adminToken}}

{ "status": "ACCEPTED", "note": "Open slot confirmed; intake scheduled" }
```

**200** — status `ACCEPTED`, `resolvedBy` the navigator, and **two**
history rows.

### 5f. Capacity actually moved

```
GET /api/providers/<same provider>
```

`openSlots` is one lower than in step 3c.

---

## 6. The waitlist — the interesting case

This is the technical deep dive. Rehearse it.

### 6a. Submit to a provider at zero capacity

```
POST /api/referrals
Authorization: Bearer {{clientToken}}

{
  "providerId": <provider with openSlots: 0>,
  "message": "Asking about availability",
  "preferredContact": "EMAIL"
}
```

**201** — `PENDING`. Submitting is allowed; capacity is checked at
review time, not submission time.

### 6b. Try to accept it

```
POST /api/referrals/<id>/decision
Authorization: Bearer {{adminToken}}

{ "status": "ACCEPTED", "note": "Trying to accept" }
```

**200** — but status comes back **`WAITLISTED`**, with the note
"No open slots at review time - added to waitlist".

> Say, slowly, because this is the point:
>
> "I asked for ACCEPTED. The system gave me WAITLISTED. The provider's
> last slot was gone, so accepting would have promised an intake that
> does not exist.
>
> The whole method is one transaction. Inside it, the provider row is
> re-read with a pessimistic write lock — `SELECT ... FOR UPDATE`.
> Without that lock, two navigators accepting at the same moment could
> both read one open slot and both decrement it.
>
> Four things then happen together or not at all: capacity adjusts, the
> status changes, `resolvedAt` and `resolvedBy` are set, and a history
> row is written. If any step fails, all of it rolls back — no
> half-decremented capacity, and no status change without its audit
> row.
>
> And the response reports what actually happened, not what was asked
> for. Silently doing something different from what the caller
> requested would be worse than failing."

### 6c. The waitlist count moved

```
GET /api/providers/<same provider>
```

`waitlistCount` is one higher.

---

## 7. The audit trail

```
GET /api/referrals/<accepted referral id>
Authorization: Bearer {{adminToken}}
```

Point at the `history` array:

```json
[
  { "fromStatus": null, "toStatus": "PENDING",
    "changedBy": "Alicia Moreno", "createdAt": "..." },
  { "fromStatus": "PENDING", "toStatus": "ACCEPTED",
    "changedBy": "Dana Whitfield", "createdAt": "..." }
]
```

> Say: no referral changes status without a row recording who moved it,
> from what, to what, and when. The status change and the audit row are
> written in the same transaction, so one cannot happen without the
> other.

### Prove it in SQL

```sql
SELECT r.id, r.status, h.to_status AS latest_history_status
FROM referral_requests r
LEFT JOIN referral_status_history h
       ON h.id = (SELECT MAX(h2.id) FROM referral_status_history h2
                  WHERE h2.referral_request_id = r.id)
WHERE h.id IS NULL OR h.to_status <> r.status;
```

**Zero rows** — every referral's current status matches the latest
entry in its history.

---

## 8. A client sees only their own

```
GET /api/referrals/mine
Authorization: Bearer {{clientToken}}
```

**200** — Alicia's referrals only.

```
GET /api/referrals/<a referral belonging to Devon>
Authorization: Bearer {{clientToken}}
```

**403** — "You do not have access to this referral"

---

## 9. Reports — the access gap

```
GET /api/reports/access-gap
Authorization: Bearer {{adminToken}}
```

Accepting providers by plan type.

> Say: this is the most useful number the system produces. It is not a
> count of rows — it is the gap between how many clinicians take
> commercial insurance and how many take Medicaid. That falls out of
> the referral workflow rather than requiring a separate survey.

---

## 10. The invariant that keeps the directory honest

```
PATCH /api/providers/<id>/capacity
Authorization: Bearer {{adminToken}}

{ "openSlots": 0, "acceptingNewClients": true }
```

**400** — "A provider cannot be accepting new clients with zero open
slots"

> Say: a directory that says someone is available when they are not is
> worse than no directory. That rule is enforced on every write path,
> not just this endpoint.

---

## Resetting between rehearsals

Re-run `seed.sql` (whole file, plain lightning bolt). It truncates and
reloads, so capacity and referrals return to their starting values.

Then verify:

```sql
SELECT (SELECT COUNT(*) FROM counties)          AS counties,
       (SELECT COUNT(*) FROM organizations)     AS organizations,
       (SELECT COUNT(*) FROM providers)         AS providers,
       (SELECT COUNT(*) FROM referral_requests) AS referrals;
```

Expect **67 / 17 / 35 / 10**.

---

## If something fails mid-demo

| Symptom | Cause | Fix |
|---|---|---|
| 401 everywhere | Token expired (24h) | Log in again |
| 403 on an admin endpoint | Using the client token | Switch tokens |
| 400 "not a valid value for 'id'" | A leftover `{id}` placeholder | Remove the braces |
| 409 on submit | That referral already exists from a previous run | Use a different provider, or re-seed |
| 500 | Check the IntelliJ console | The stack trace names the line |

**Say it out loud if something breaks.** "That's a stale token, give me
five seconds" lands far better than silent clicking.
