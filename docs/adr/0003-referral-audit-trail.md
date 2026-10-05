# ADR 0003 — Referral status history as an append-only audit trail

## Status
Accepted

## Context

A referral's status is the thing both sides of this system care about.
The person who sent it wants to know whether anyone has looked at it.
The navigator reviewing it needs to know what was already decided, and
by whom.

Holding only a `status` column and overwriting it answers neither
question. It loses who changed it, when, from what, and why — and once
lost, it cannot be reconstructed.

This matters more here than in a typical CRUD application. A referral is
a record of someone asking for help with their mental health. "Your
request was declined" with no record of who declined it or when is not a
system anyone should have to trust.

There is also a practical reason: capacity moves between a request being
submitted and a navigator reviewing it, so a referral can resolve to a
status nobody explicitly asked for (see ADR-0003's relationship to the
capacity logic). Without a trail, that looks like a bug rather than a
documented decision.

## Decision

Every status transition writes a `referral_status_history` row **in the
same transaction** as the status change.

- `ReferralRequest.transitionTo(status, note, actor)` is the only way to
  move a referral. It changes the status and appends the history row in
  one call, so a status change without its audit row is not something a
  caller can produce by accident.
- Rows are **append-only by convention** — written, never updated or
  deleted.
- Each row records `from_status`, `to_status`, a note, who made the
  change, and when.
- The creation of a referral writes its own row (`null → PENDING`), so
  the trail is complete from the beginning rather than starting at the
  first change.
- `changed_by` is `ON DELETE SET NULL`: removing a user account does not
  erase the record of what they did. The event survives; the attribution
  does not.
- The database enforces
  `CHECK (from_status IS NULL OR from_status <> to_status)` — a "change"
  that changes nothing is a bug, not an event.

## Consequences

**Gained**

- A complete timeline per referral, surfaced directly in the UI.
- The current status is **verifiable** against the trail. The latest
  row's `to_status` must equal the referral's `status`, and that is
  checkable in one query:

  ```sql
  SELECT r.id, r.status, h.to_status
  FROM referral_requests r
  LEFT JOIN referral_status_history h
         ON h.id = (SELECT MAX(h2.id) FROM referral_status_history h2
                    WHERE h2.referral_request_id = r.id)
  WHERE h.id IS NULL OR h.to_status <> r.status;
  ```

  Zero rows means the two have never disagreed.
- When the system resolves a referral to something other than what was
  requested, the reason is on the record rather than only in a log file.

**Accepted costs**

- One extra insert per transition.
- The history table grows without bound. At this scale that is
  irrelevant; a long-lived system would archive rather than delete,
  since deleting is the thing this decision exists to prevent.
- `created_at` is assigned in the constructor rather than by
  `@CreationTimestamp`. This is deliberate and was learned the hard way:
  the row is persisted by **cascade**, which happens at flush, so with
  `@CreationTimestamp` the field was still null while the service mapped
  its response — and the mapping threw a `NullPointerException`.

## Verification

`ReferralServiceTest` asserts that every transition produces a history
row with the right from/to statuses and actor, that a withdrawal is
attributed to the client while leaving `resolvedBy` null, and that the
latest row always matches the current status.
`ReferralRequestTest.transition_toSameStatusIsRejected` covers the
no-op guard.
