# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

EventHive — a ticketed-event booking platform whose central design problem is preventing double-booking of seats. Spring Boot 4 / Java 21 backend (`backend/`), React 19 + Vite frontend (`frontend/`), Postgres + Redis via Docker Compose.

## Commands

Dev infrastructure (Postgres on **5333**, Redis on **6377** — non-default ports, both hardcoded in `application.yaml`):

```bash
docker compose up -d
```

Backend (`cd backend`):

```bash
./gradlew bootRun          # app on :8181; loads backend/.env into the process env
./gradlew build
./gradlew test
./gradlew test --tests "com.eventhive.integration.BookingIntegrationTest"
./gradlew test --tests "*BookingIntegrationTest.shouldCreateBooking"
```

`bootRun` has a custom task hook that reads `backend/.env` and injects each `KEY=value` line as an environment variable. Required keys: `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `JWT_SECRET_KEY`, `STRIPE_SECRET_KEY`, `STRIPE_PUBLISH_KEY`, `STRIPE_SIGNING_KEY`. Tests do **not** need `.env` — they override these via `@TestPropertySource`.

Frontend (`cd frontend`):

```bash
npm run dev      # Vite dev server
npm run build
npm run lint     # oxlint
```

CI (`.github/workflows/ci.yaml`) runs only `backend/./gradlew test` on PRs to `main`.

## Backend architecture

### Package layout
Vertical slices by domain under `com.eventhive`: `users`, `venues`, `events`, `seats`, `tiers`, `bookings`, `payments`, plus cross-cutting `auth`, `security`, `redis`, `stripe`, `config`, `exception`.

Each slice follows the same file set and it is expected that new domains match it:
`Entity` / `Repository` (Spring Data JPA) / `Service` / `Controller` / `XxxDTO` + `XxxDTOMapper` (a `Function<Entity, DTO>` `@Service`) / `XxxRegistrationRequest` + `XxxUpdateRequest` records / `XxxSecurity` component for ownership checks. Constructor injection via Lombok `@RequiredArgsConstructor` throughout.

`XxxSummaryDTO` types exist to expose a related entity across slice boundaries without leaking the entity (e.g. `venues.EventSummaryDTO` is returned by booking endpoints). Note they sometimes live in the *referenced* slice's package, not the referencing one.

### Seat locking (the core concurrency mechanism)
`redis.SeatLockService` holds a Redis key `seat-lock:{eventId}:{seatId}` whose value is the locking user's id, acquired with `SETNX`. Its TTL is `SeatLockService.SEAT_HOLD` (**31 minutes**), which is also the Stripe Checkout Session's `expires_at`. Keep the two equal: the `PENDING` booking blocks the seat in the DB for the whole session anyway. Release is done through a Lua script (`src/main/resources/scripts/releaseLock.lua`) so the delete is compare-and-delete on the owning user — never release with a plain `DEL`.

The DB is the final guard: the V5 partial unique index allows one `PENDING`/`CONFIRMED` booking per (event, seat). Before locking, `BookingService.addBooking` looks up the active booking for the seat:
- `CONFIRMED` → `DuplicateResourceException`.
- `PENDING` within the window, same user → **resume**: returns the booking's still-open session URL (`checkoutSessionId` column, V7) with HTTP 200 and `resumed: true`.
- `PENDING` within the window, another user → `SeatAlreadyLockedException`.
- `PENDING` past the window (missed expiry webhook) → mark it `EXPIRED`, expire its session, and continue.

It then takes the lock and persists the booking, releasing the lock on any `RuntimeException`. A DB unique violation from a lost race is rethrown as `SeatAlreadyLockedException`.

### Pricing (`tiers`)
Customers never send a price. Organisers and admins define `PriceTier`s per event (`/api/v1/events/{eventId}/tiers`), each built from one or more `SeatRange`s (rows ordered A–Z then AA–ZZ; seat-number bounds are optional) matched against the event venue's seats. Assignments live in `price_tier_seats`, whose `UNIQUE(event_id, seat_id)` means a seat has at most one price per event. A seat with no tier is not on sale. `addBooking` copies the tier's `priceCents` onto the booking, so later tier price changes don't affect existing bookings. A tier can't be deleted while its seats have `PENDING`/`CONFIRMED` bookings. `GET /api/v1/events/{eventId}/seats` returns the seat map (tier, price, availability).

### Payment flow (Stripe hosted checkout)
1. `POST /api/v1/bookings` → lock seat → save `Booking` with `PENDING` → `StripeHostedCheckoutService.checkout(booking)` creates a Checkout Session carrying `bookingId` in session metadata → response returns the DTO plus the Stripe redirect URL.
2. Stripe calls `POST /api/v1/stripe/webhooks` (permit-all; verified by signature against `stripe.webhook.signing`). `WebhookController` dispatches on event type to `BookingService.handleSuccessPayment` / `handleExpiredPayment`, recovering the booking from session metadata.
3. `handleSuccessPayment` is idempotent: if the payment intent is already recorded for the booking, it does nothing. A `PENDING` booking is confirmed. Otherwise (the booking was already `EXPIRED`/`CANCELLED`, or was paid with a different intent), the new intent is refunded, and a `REFUNDED` `Payment` is recorded only if the booking has none (one payment per booking).
4. `handleExpiredPayment` only moves `PENDING` → `EXPIRED`; for any other status it logs and returns 200, so Stripe stops retrying.

`POST /api/v1/bookings/{id}/cancel` (owner or admin), and an admin `PUT` with `status: CANCELLED`, go through `BookingService.cancel`. Customers can cancel only up to `BookingService.CANCELLATION_CUTOFF` (48h) before the event starts; admins can cancel at any time. In `cancel`, a `PENDING` booking has its Stripe session expired and its seat lock released; a `CONFIRMED` booking is refunded (`Payment` → `REFUNDED`). Refunds go through `StripeService.initiateRefund`, which uses the idempotency key `refund_{paymentIntentId}`.

Bookings are only ever confirmed through the webhook path, never by the HTTP request that started checkout.

### Security
Stateless JWT resource server (`security.SecurityConfig`), HS256 with `jwt.secret`; the user id travels in a custom `userId` claim, not the subject. Roles: `USER`, `EVENT_ORGANISER`, `ADMIN`.

Two layers, both of which must be kept in sync when adding endpoints:
- Path/method rules in `SecurityConfig.filterChain` (coarse, uses `/api/v*/` matchers).
- `@PreAuthorize` on controller methods for ownership, e.g. `@PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isOwner(#id, authentication.token.claims['userId'])")`.

Refresh tokens live in `auth/refresh` with their own table (`V2` migration) and support `logout` / `logout-all`.

Errors: throw the domain exceptions in `com.eventhive.exception`; `GlobalExceptionHandler` maps them to the `ApiError` response shape. Don't build error responses in controllers.

### Database
Flyway-managed, `ddl-auto: validate` — **schema changes require a new `V{n}__*.sql` in `src/main/resources/db/migration`**, never an entity-only change. Existing migrations encode important invariants (unique payment intent id, unique booking per seat scoped by status, one payment per booking).

`config/*Seeder.java` are `ApplicationRunner`s ordered with `@Order` (Venue → Seat → Event → PriceTier, plus `AdminSeeder`), gated on `eventhive.seed.enabled` and disabled in tests.

### Tests
- `AbstractRepositoryTest` — `@DataJpaTest` against a real Postgres.
- `AbstractWebIntegrationTest` — `@SpringBootTest(RANDOM_PORT)`, injects fake JWT/Stripe/admin properties, truncates all tables after each test via `TestUtility.clearDatabase`.
- Both extend `TestContainerInitialiser`, which starts **one** static `postgres:16.0` Testcontainer for the whole JVM. Add any new table to `TestUtility.clearDatabase`'s `TRUNCATE` list or tests will leak state.
- Stripe is mocked with `@MockitoBean` in integration tests (see `StripePaymentIntegrationTest`); Redis locking is covered by `RedisLockTest` against the Compose/CI Redis on 6377, so that instance must be up to run the full suite locally.

## Frontend

Minimal Vite + React 19 SPA, JS (not TS), Tailwind v4 (via `@tailwindcss/vite`), no router or state library — `App.jsx` switches between tabbed pages in `src/pages/`. Lint is oxlint, not ESLint.

- `src/api.js` is the only place that calls `fetch` (base `http://localhost:8181/api/v1`). The access token is kept in memory; the refresh token is the backend's httpOnly cookie, so requests use `credentials: 'include'`, and a 401 triggers one refresh-and-retry. The public auth endpoints are called without a bearer header, because Spring rejects expired tokens even on permit-all routes.
- `src/components/ui.jsx` holds presentational primitives; hooks and formatters live in `src/hooks.js` (kept separate for Fast Refresh).
- The dev server is pinned to port **5173**, which must match `eventhive.frontend-url` in `application.yaml`. That value drives both the CORS allowed origin (`SecurityConfig.corsConfigurationSource`) and Stripe's success/cancel redirects (`/?checkout=success|cancelled&bookingId=…`).
