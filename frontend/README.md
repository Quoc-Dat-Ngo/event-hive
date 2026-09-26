# EventHive Frontend

A minimal React 19 + Vite SPA over the EventHive API. It exists to exercise the backend end to end — browse events, pick a seat off a live seat map, go through Stripe Checkout, come back, and manage or cancel the booking — without becoming a project of its own.

## How this was built

**This interface was produced through an agentic workflow with [Claude Code](https://claude.com/claude-code), and that is the point of it rather than an aside.** The backend is where the design work of this project lives: distributed seat locking, a webhook-driven payment lifecycle, tier pricing, refund and cancellation edge cases. A UI was needed to drive all of that against a real Stripe account, but hand-building one would have spent days on presentational code that proves nothing about the system.

So the work was split along the line where each side is strongest:

- **The API contract was specified by hand.** Endpoints, status codes, DTO shapes, auth semantics and the failure cases worth surfacing were decided in the backend and written down in [`../backend/README.md`](../backend/README.md) — which is what the agent read as its source of truth.
- **The agent wrote the client and the screens**, working from that contract: `src/api.js`'s fetch layer, the data-fetching hooks, the presentational primitives, and the four pages composed from them.
- **Review stayed manual.** Every behaviour that touches money or concurrency was checked against the backend rather than trusted: the `201` vs `200` split on a resumed checkout, the 48-hour cancellation cutoff mirrored client-side while the server remains authoritative, `404` treated as "not paid yet" on the payments lookup, and the refresh-and-retry path on a `401`.

The result is a UI whose value is coverage of the API surface, not visual ambition — which is exactly the trade this workflow is good at. What the agent is doing here is transcribing a contract into a client, and a contract is a thing it can be held to.

### What that produced, concretely

Every `fetch` in the app goes through `src/api.js`, and the pages between them cover the whole customer and operator path:

| Area | Endpoints driven |
| --- | --- |
| Auth & session | `POST /auth/login`, `/auth/register`, `/auth/refresh-token`, `/auth/logout`, `/auth/logout-all` |
| Browsing | `GET /events`, `GET /events/{id}/host` |
| Seat picking | `GET /events/{id}/seats` (the seat map), `GET /events/{id}/tiers` |
| Booking & payment | `POST /bookings`, `POST /bookings/{id}/cancel`, `GET /bookings/{id}` + `/event`, `/seat`, `/payments` |
| Account | `GET /users/{id}`, `PUT /users/{id}`, `GET /users/{id}/bookings` |
| Organiser | `POST /events`, `DELETE /events/{id}`, `GET`/`POST`/`PUT`/`DELETE` `/events/{id}/tiers`, `GET /events/{id}/bookings` |
| Admin | `GET`/`POST`/`DELETE` `/venues`, `POST /seats`, `GET`/`POST`/`DELETE` `/users`, `GET`/`PUT` `/bookings`, `GET /payments` |

## Running

```bash
npm install
npm run dev      # Vite dev server on http://localhost:5173
npm run build
npm run lint     # oxlint, not ESLint
```

The backend must be running on `:8181` (see [`../backend/README.md`](../backend/README.md)). **The dev server port is pinned to `5173` with `strictPort: true`** because it has to match `eventhive.frontend-url` in `application.yaml`, which is both the single allowed CORS origin and the base of Stripe's success/cancel redirects. If Vite silently moved to `5174`, logins would fail CORS and Stripe would redirect into nothing.

## Stack and shape

React 19, Vite, Tailwind v4 through `@tailwindcss/vite`, oxlint. Plain JavaScript — no TypeScript. No router and no state library: `App.jsx` holds the session and switches between tabbed pages.

```
src/
├── api.js              The only place that calls fetch — token handling, refresh-and-retry, ApiError
├── App.jsx             Session restore, tab switching, the Stripe return banner
├── hooks.js            useFetch / useAction, date and money formatters, useTheme
├── components/ui.jsx   Presentational primitives (Card, Button, Input, Badge, ErrorBox, …)
├── index.css           Tailwind entry + the semantic colour tokens the components use
└── pages/
    ├── AuthPage.jsx    Login / register
    ├── EventsPage.jsx  Event list, seat map booking, tier management, per-event bookings
    ├── AccountPage.jsx Profile, my bookings, booking detail, cancel, Stripe return banner
    └── ManagePage.jsx  Organiser and admin forms — events, venues, seats, users, bookings, payments
```

Hooks and formatters live in `hooks.js` rather than alongside the components on purpose: a module that exports both components and non-components breaks Fast Refresh.

## Things worth knowing before changing it

**Tokens.** The access token is held in a module-level variable in `api.js` — deliberately not `localStorage`, so it is not readable by injected script. The refresh token is the backend's httpOnly cookie, so every request sends `credentials: 'include'`, and the session is restored on load by calling `/auth/refresh-token` with no token at all. A `401` on any authenticated request triggers one refresh-and-retry; concurrent callers share a single in-flight refresh, because refresh tokens rotate on use and a second concurrent refresh would present an already-revoked token and get the whole family revoked.

**The public auth endpoints are called without a bearer header.** Spring rejects an expired token even on a permit-all route, so an expired token in memory would break login itself. `PUBLIC_PATHS` in `api.js` is what keeps that from happening.

**Booking is a two-status endpoint.** `POST /bookings` answers `201` for a new booking and `200` with `resumed: true` when the caller already had an open checkout for that seat. Both carry a `url` to redirect to, so the UI redirects on either — the distinction only matters for what it tells the user.

**Stripe returns to `/?checkout=success|cancelled&bookingId=…`.** `App.jsx` reads those params on load and shows `CheckoutBanner`; a `success` return does **not** mean the booking is confirmed, because confirmation only ever happens on the webhook. The banner therefore fetches the booking's real status rather than trusting the redirect.

**The 48-hour cancellation cutoff is duplicated client-side** to disable the button early and explain why. The server enforces it regardless — the client copy is affordance, not authorisation.

**Prices are never sent.** Booking posts only `{ eventId, seatId }`; the price comes from the event's tiers. A seat with no tier is not on sale and the seat map renders it as unavailable.
