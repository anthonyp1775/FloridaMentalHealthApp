# ADR 0002 — Return DTOs, never entities

## Status
Accepted

## Context

JPA entities are shaped for persistence, not for transmission, and the
difference is not cosmetic.

**They contain fields that must never reach a client.** `User` holds a
BCrypt password hash. Serializing a `User` would publish it.

**Their relationships are bidirectional.** `ReferralRequest` holds a
list of `ReferralStatusHistory`, and each history row holds a reference
back to its request. Handing either to Jackson means following that
cycle until the serializer gives up.

**Their associations are lazy.** With `spring.jpa.open-in-view=false`,
serializing an entity outside a transaction throws
`LazyInitializationException` the moment Jackson touches an unloaded
association — and which associations are loaded depends on which query
fetched the object, so the failure is intermittent.

**Their shape is a storage decision.** Letting the database schema
become the public API contract means any schema change is a breaking API
change.

## Decision

Every endpoint returns a DTO. Entities never leave the service layer.

- DTOs are Java **records**, grouped by domain in one class per area
  (`ProviderDtos.Summary`, `ReferralDtos.Response`, and so on) rather
  than one file per record.
- Mapping happens in the service, **inside the transaction**, which is
  the only place it is safe to touch a lazy association.
- Request and response shapes are separate types. What a client may send
  is not the same set of fields as what it receives.
- Collections map to sorted lists of names, so the UI renders the same
  order every time.

## Consequences

**Gained**

- The password hash cannot leak. There is no path from a `User` entity
  to a response body.
- No serialization cycles, and no `LazyInitializationException` from
  Jackson — if an association is needed, the mapper loads it while the
  transaction is still open, and the compiler says so.
- The API contract is stable across schema changes. A renamed column is
  a one-line change in a mapper, not a breaking change for the frontend.
- Validation annotations live on the request records, where they
  describe what a caller may send, rather than on entities, where they
  would describe what the database will accept.

**Accepted costs**

- More code. Each domain has request and response types plus a mapper,
  and a new field has to be added in two places.
- Mappers are written by hand rather than generated. At this size that
  is a feature — the mapping is where "which fields are public" is
  decided, and that decision should be visible.

## Verification

Response-shape assertions in the controller tests check field names and
values directly. `ProviderServiceTest.getById_sortsCollectionNames`
pins the sorted-collection behaviour that makes responses stable.
