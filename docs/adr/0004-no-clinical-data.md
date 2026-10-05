# ADR 0004 — Store no clinical data and run no screening logic

## Status
Accepted

## Context

The obvious feature for a mental health application is an intake
questionnaire — score the answers, judge severity, route accordingly.

It is also the wrong feature for this system, for three separate
reasons.

**It imitates a clinical instrument without being one.** Real screening
tools are validated against outcomes and interpreted by clinicians. A
scoring routine written for a course project looks the same to a person
in distress and carries none of that weight. Someone could be told they
are fine by software that has no basis for saying so.

**It changes what the system is holding.** The moment responses are
stored, every record becomes protected health information. That brings
encryption at rest, access logging, retention policy, breach
notification, and a compliance posture this project cannot honestly
meet. Claiming to meet it would be worse than not attempting it.

**It does not answer the question people actually have.** The barrier
for someone looking for care in Florida is not "how severe is this." It
is: who can see me, do they take my insurance, do they speak my
language, and how long is the wait. None of those are clinical
questions.

## Decision

The system stores **no clinical data and runs no screening logic**.

What it holds:

- Account details — name, email, password hash, phone.
- Self-described preferences — preferred county, language, insurance
  plan, telehealth preference, contact preference.
- Referral requests, with a free-text message the person writes
  themselves.

What it never holds: diagnoses, assessments, symptom scores, clinical
notes, treatment history, medication lists.

Supporting choices that follow from this:

- Search filters on stated preferences only — county, insurance,
  language, focus area, modality.
- The referral form's message box is free text, and its placeholder asks
  about **scheduling**, not symptoms.
- No endpoint accepts a clinical field, so there is no path by which one
  could arrive.
- Crisis resources render on **every screen including before login**,
  because a directory cannot help someone in an acute crisis and should
  not behave as though it can.
- The statewide resources page is public and its contents are **real**,
  while every provider in the directory is sample data.

## Consequences

**Gained**

- The application is entirely outside PHI handling. That is not a gap in
  the design; it is the design.
- A smaller promise, kept, instead of a larger one, broken.
- Search quality is unaffected, because the useful filters were never
  the clinical ones.

**Accepted costs**

- No triage, no severity routing, no "recommended for you."
- A navigator sees only what the person chose to write.
- The system cannot claim to match people to appropriate levels of care.
  It is a directory with a referral workflow, and it says so.

**What would have to change to revisit this**

Clinical oversight, a validated instrument rather than an invented one,
and a genuine compliance posture — encryption at rest, access logging,
retention policy. Any one of those missing makes the feature worse than
its absence.
