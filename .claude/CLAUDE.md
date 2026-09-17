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
Vertical slices by domain under `com.eventhive`: `users`, `venues`, `events`, `seats`, `bookings`, `payments`, plus cross-cutting `auth`, `security`, `redis`, `stripe`, `config`, `exception`.

Each slice follows the same file set and it is expected that new domains match it:
`Entity` / `Repository` (Spring Data JPA) / `Service` / `Controller` / `XxxDTO` + `XxxDTOMapper` (a `Function<Entity, DTO>` `@Service`) / `XxxRegistrationRequest` + `XxxUpdateRequest` records / `XxxSecurity` component for ownership checks. Constructor injection via Lombok `@RequiredArgsConstructor` throughout.

`XxxSummaryDTO` types exist to expose a related entity across slice boundaries without leaking the entity (e.g. `venues.EventSummaryDTO` is returned by booking endpoints). Note they sometimes live in the *referenced* slice's package, not the referencing one.

### Seat locking (the core concurrency mechanism)
`redis.SeatLockService` holds a Redis key `seat-lock:{eventId}:{seatId}` whose value is the locking user's id, with a **5-minute TTL**, acquired with `SETNX`. Release is done through a Lua script (`src/main/resources/scripts/releaseLock.lua`) so the delete is compare-and-delete on the owning user — never release with a plain `DEL`.

`BookingService.addBooking` acquires the lock before persisting the booking and releases it in a `catch (RuntimeException)` before rethrowing. Failure to acquire throws `SeatAlreadyLockedException`.

### Payment flow (Stripe hosted checkout)
1. `POST /api/v1/bookings` → lock seat → save `Booking` with `PENDING` → `StripeHostedCheckoutService.checkout(booking)` creates a Checkout Session carrying `bookingId` in session metadata → response returns the DTO plus the Stripe redirect URL.
2. Stripe calls `POST /api/v1/stripe/webhooks` (permit-all; verified by signature against `stripe.webhook.signing`). `WebhookController` dispatches on event type to `BookingService.handleSuccessPayment` / `handleExpiredPayment`, recovering the booking from session metadata.
3. `handleSuccessPayment` is idempotent and handles the race where the seat was already confirmed by a different payment intent during the lock TTL: it initiates a Stripe refund and sets the `Payment` to `REFUNDED`.

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

`config/*Seeder.java` are `ApplicationRunner`s ordered with `@Order` (Venue → Seat → Event, plus `AdminSeeder`), gated on `eventhive.seed.enabled` and disabled in tests.

### Tests
- `AbstractRepositoryTest` — `@DataJpaTest` against a real Postgres.
- `AbstractWebIntegrationTest` — `@SpringBootTest(RANDOM_PORT)`, injects fake JWT/Stripe/admin properties, truncates all tables after each test via `TestUtility.clearDatabase`.
- Both extend `TestContainerInitialiser`, which starts **one** static `postgres:16.0` Testcontainer for the whole JVM. Add any new table to `TestUtility.clearDatabase`'s `TRUNCATE` list or tests will leak state.
- Stripe is mocked with `@MockitoBean` in integration tests (see `StripePaymentIntegrationTest`); Redis locking is covered by `RedisLockTest` against the Compose/CI Redis on 6377, so that instance must be up to run the full suite locally.

## Frontend

Minimal Vite + React 19 SPA, JS (not TS), no router or state library yet; `src/App.jsx` is effectively the whole app. Lint is oxlint, not ESLint. Backend API base is `http://localhost:8181`.
