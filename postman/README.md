# Postman collection — Florida Mental Health API

**39 requests across 6 folders.** Converted from the Bruno collection in
`../bruno/` — same requests, same assertions.

## Importing

1. Postman → **Import** → drop in both files:
   - `florida-mental-health-api.postman_collection.json`
   - `Local.postman_environment.json`
2. Select **Local** from the environment dropdown, top right.
3. Set `password` in the environment to the one you hashed into
   `seed.sql`. Everything else is already filled in.

## Running

The backend must be on `localhost:8080` with the database seeded.

**Run the whole collection in order** (Collection → Run). Order matters:
`01 Auth` stores `clientToken` and `adminToken` into the environment,
and every later request uses them. Running a single folder on its own
will 401 unless Auth has run first in the same session.

Expected: **39 passed, 0 failed.**

## What is covered

| Folder | Requests | What it proves |
|---|---|---|
| 01 Auth | 5 | Login for both roles, token storage, failures stay generic |
| 02 Catalog | 5 | Reference data; ids captured for later filters |
| 03 Providers | 8 | Search and filters, detail, shortlist, the capacity invariant |
| 04 Referrals | 11 | The full workflow, including ACCEPTED → WAITLISTED |
| 05 Reports | 4 | Access gap, county capacity, and that clients are refused |
| 06 Security | 6 | No token, malformed, tampered, wrong method, unknown path |

**`06 Security` is the part the JUnit tests cannot reach.** The
controller tests use standalone MockMvc, where `@PreAuthorize` is not
active because Spring builds no proxy. Role enforcement is verified
here instead, against the real filter chain with real tokens — which is
the honest level for it. See `../docs/testing.md`.

## Conversion notes

Bruno and Postman express the same ideas with different names. Both use
Chai, so the assertion bodies carried over unchanged; only the
surrounding API differs:

| Bruno | Postman |
|---|---|
| `res.getStatus()` | `pm.response.code` |
| `res.body` | `pm.response.json()` |
| `test(...)` | `pm.test(...)` |
| `expect(...)` | `pm.expect(...)` |
| `bru.setEnvVar(k, v)` | `pm.environment.set(k, v)` |
| `bru.getEnvVar(k)` | `pm.environment.get(k)` |

Each test script opens with three lines that read the response once:

```js
const status = pm.response.code;
let body = {};
try { body = pm.response.json(); } catch (e) { /* no JSON body */ }
```

The `try/catch` matters for the 204 on `DELETE /api/providers/{id}/save`,
which has no body at all — `pm.response.json()` would throw.

The Bruno collection remains in `../bruno/` and still runs. Both describe
the same 39 requests; neither is derived from the other at runtime.
