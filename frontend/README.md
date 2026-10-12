# Frontend — Florida Mental Health App

React 18 + Vite. Talks to the Spring Boot API on `localhost:8080`.

## Running it

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. The backend must already be running —
start the Spring Boot app first, or every screen shows "Cannot reach the
server."

`.env` points at the API:

```
VITE_API_BASE_URL=http://localhost:8080/api
```

`SecurityConfig` already allows CORS from `localhost:5173` and
`127.0.0.1:5173`, so no extra configuration is needed. (`vite.config.js`
also proxies `/api` as a fallback — delete the `VITE_API_BASE_URL` line
to use the proxy instead.)

Sign in with a seeded account:

| Account | Email | Sees |
|---|---|---|
| Client | `alicia.moreno@example.com` | Search, saved, own requests |
| Navigator | `navigator@carepathfl.org` | ...plus Queue, Directory, Reports |

Passwords are whatever you hashed into `seed.sql`.

## What is where

```
src/
  api.js                  axios instance + one function per endpoint
  utils.js                enum → plain English, dates, availability
  hooks.js                useFetch, useDebounce
  resources.js            real crisis/helpline data (NOT sample data)
  index.css               design tokens + every component style

  context/
    AuthContext.jsx       who is signed in; useAuth()
    ThemeContext.jsx      light/dark; useTheme()

  components/
    CrisisBanner.jsx      988 bar, on every screen including login
    Layout.jsx            masthead + nav, wraps all signed-in routes
    ProtectedRoute.jsx    route guard (convenience — the API enforces it)
    ProviderCard.jsx      one search result
    SearchFilters.jsx     the five dropdowns + two toggles
    ReferralForm.jsx      submitting a request
    StatusTimeline.jsx    a referral's audit trail
    Modal.jsx             accessible dialog (Esc, focus, backdrop)
    Table.jsx             report tables
    ui.jsx                Button, Field, Input, Select, Tag, Notice, ...

  pages/
    AuthPage.jsx                sign in / create account
    SearchPage.jsx              THE CORE SCREEN (useReducer)
    ProviderDetailPage.jsx      full detail + referral form
    SavedPage.jsx               shortlist
    MyReferralsPage.jsx         own requests + history
    ProfilePage.jsx             stated preferences
    AdminQueuePage.jsx          navigator queue + decisions
    AdminProvidersPage.jsx      capacity editing
    ReportsPage.jsx             access gap, volume, county capacity
    StatewideResourcesPage.jsx  real helplines (public route)
    NotFoundPage.jsx
```

## Where each React requirement lives

| Requirement | Where |
|---|---|
| Functional components | all 21 of them |
| `useState` | every form; `AuthPage`, `ProfilePage`, `ReferralForm` |
| `useEffect` | `hooks.js` (`useFetch`, `useDebounce`), `ThemeContext`, `Modal` |
| `useReducer` | `SearchPage` — filter state, so a filter change can never forget to reset the page |
| `useMemo` | `SearchPage` (query params), `ProfilePage` (name→id), `AdminProvidersPage` (client-side filter), `ReportsPage` (bar scale) |
| `useCallback` | `SearchPage` dispatchers, `AdminQueuePage`, `ProviderDetailPage` |
| `useRef` | `hooks.js` race guard, `Modal` focus |
| Custom hooks | `useFetch`, `useDebounce`, `useAuth`, `useTheme` |
| Context API | `AuthContext`, `ThemeContext` |
| Routing | `App.jsx` — nested routes, route params, guards, 404 |
| Conditional rendering | loading / error / empty / data on every page |
| Lists with keys | `ProviderCard` lists, tags, timeline, tables |
| Forms + validation | field-level errors come from the API's `fieldErrors` map |

## Three things worth defending in the demo

1. **Availability is the card's left edge, not a badge.** Whether a
   provider can actually see you is the fact other directories bury. It
   is always paired with text, never color alone.

2. **`AdminQueuePage` tells you when it did something else.** Click
   Accept on a provider whose last slot is gone and the notice says you
   asked for Accepted and got Waitlisted, and why. The backend decides
   that under a write lock; this screen just refuses to show a success
   message for something that did not happen.

3. **`/resources` is public and real.** Every provider in this directory
   is sample data (`Example ...`). The crisis page is not, and it does
   not sit behind the login — someone who needs 988 should not have to
   make an account first.

## Before presenting

- Re-check the phone numbers and URLs in `src/resources.js` against each
  organization's own site. It is the one place in this project where a
  wrong value could actually matter.
