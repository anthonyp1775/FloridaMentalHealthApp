# Entity Relationship Diagram

![ERD](erd.png)

Reverse-engineered from the live `fl_mental_health_db` schema in MySQL
Workbench, so it reflects what the database actually contains rather
than what `schema.sql` intended. The schema file remains the source of
truth: `backend/src/main/resources/db/schema.sql`.

**18 tables.** To regenerate after a schema change: Workbench →
**Database → Reverse Engineer** → select `fl_mental_health_db` →
Execute → **File → Export → Export as PNG**.

---

## How the diagram is arranged

Left to right, the layout follows one person's journey: **a person on
the left, the care they are looking for on the right, and the referral
between them.**

| Column | Tables | What it holds |
|---|---|---|
| 1 — Identity | `roles`, `user_roles`, `users` | Who you are |
| 2 — Workflow | `saved_providers`, `referral_requests`, `referral_status_history`, `client_profiles` | What you did |
| 3 — Directory | `providers`, `organizations`, `counties` | Where care happens |
| 4 — Join tables | `provider_specialties`, `provider_populations`, `provider_languages`, `provider_insurance` | The many-to-many links |
| 5 — Lookups | `specialties`, `populations`, `languages`, `insurance_plans` | What the filters offer |

`client_profiles` sits at the bottom of column 2 rather than beside
`users`, because it points at `counties`, `languages` and
`insurance_plans` — all in the lower right. Placing it there keeps three
relationship lines short instead of cutting them diagonally across the
middle.

---

## Relationships

**One-to-one**
- `users` ↔ `client_profiles` — **shared primary key**

**One-to-many**
- `counties` → `organizations`
- `organizations` → `providers`
- `users` → `referral_requests`, `saved_providers`
- `providers` → `referral_requests`, `saved_providers`
- `referral_requests` → `referral_status_history`

**Many-to-many** (each through a pure join table)
- `users` ↔ `roles`
- `providers` ↔ `specialties`
- `providers` ↔ `populations`
- `providers` ↔ `languages`
- `providers` ↔ `insurance_plans`

---

## Three things worth a sentence each

**1. The shared primary key.**
`client_profiles` has no id of its own — `user_id` is both its primary
key and its foreign key to `users`, mapped with `@MapsId`. That makes a
second profile for one person *structurally* impossible, which is
stronger than a unique constraint: there is no second column that could
be unique. One person, one profile, enforced by the shape of the table
rather than by a rule that could be dropped.

**2. The four join tables carry no surrogate id.**
`provider_specialties` and its three siblings have a composite primary
key of the two foreign keys and nothing else — no `id`, no timestamps.
There is nothing else to say about the row; its entire meaning is "these
two things are linked." Compare `saved_providers`, which *does* have its
own id, because it carries a note and a created-at and is read directly
rather than only traversed.

**3. `referral_status_history` is why the system can be trusted.**
A referral's status lives in `referral_requests.status`, but no
transition is written without a history row recording who moved it, from
what, to what, and when — in the same transaction. `changed_by` is
`ON DELETE SET NULL`, so deleting a user account does not erase the
record of what they did: the event survives, the attribution does not.
The database also enforces
`CHECK (from_status IS NULL OR from_status <> to_status)`, because a
"change" that changes nothing is a bug, not an event.

See ADR-0003 for the reasoning.

---

## Verifying the diagram against the database

```sql
SELECT COUNT(*) AS tables_in_schema
FROM information_schema.tables
WHERE table_schema = 'fl_mental_health_db';
```

Expect **18**. If the diagram shows a different number, it is stale.
