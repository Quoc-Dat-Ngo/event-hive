# Architecture Decisions

A record of the significant design decisions behind EventHive and the reasoning — including the alternatives that were rejected and why.

> **How to use this document.** Each section states the decision as implemented today, then poses the questions worth answering. Replace each `>` prompt with your own reasoning. Anything you cannot answer is a decision worth revisiting, not a gap to paper over.
>
> Delete this block once the document is filled in.

**Legend:** ✅ decided & implemented · 🚧 implemented, reasoning not yet written up · ❓ open question · ⏭️ deferred

---

## 1. Problem Framing

**The problem:** two customers buying the same seat at the same moment, across a checkout that spans minutes and an external payment provider.

> - What exactly counts as "sold" in this system — the lock, the `PENDING` booking, or the `CONFIRMED` booking? Why draw the line there?
> - What is the correctness bar? Is overselling ever acceptable if compensated by a refund, or must it be structurally impossible?
> - Which is worse for this product: rejecting a customer who could have been served (false conflict), or momentarily double-selling and refunding (false success)? What did that answer change?
> - What scale is this designed for — concurrent buyers per event, events per venue, seats per event? What would break first at 10×?

---

## 2. Overall Shape

### 2.1 Monolith over microservices ✅

A single Spring Boot service owns users, events, seats, bookings and payments.

> - What would splitting bookings/payments into their own service have bought, and what would it have cost?
> - Would a split have forced distributed transactions or a saga? Is avoiding that the real reason for the monolith?
> - Where is the seam you would cut first if this had to be split later? Does the current package layout preserve that seam?

### 2.2 Vertical slices over layered packages ✅

Packages are organised by domain (`bookings`, `payments`, …), each containing its own entity, repository, service, controller and DTOs — rather than global `controllers/`, `services/`, `repositories/` packages.

> - What made slices the better fit here? Change locality, or something else?
> - Cross-slice types (`EventSummaryDTO` living in `venues`, consumed by `bookings`) currently leak across boundaries. Is that acceptable coupling, or a sign a shared read-model package is needed?
> - `BookingService` depends on five repositories plus Redis plus two Stripe services. Does that violate the slice boundary, or is booking legitimately the orchestrator?

### 2.3 Spring Boot 4 / Java 21 ✅

> - Why the newest Spring Boot major rather than the mature 3.x line? What did you gain, and what breakage did you absorb?
> - Which Java 21 features actually shaped the code — records for DTOs, pattern-matching switches in the webhook handler, virtual threads (are they enabled)?

---

## 3. Concurrency & Seat Locking

This is the core of the system; these answers matter most.

### 3.1 Redis distributed lock over database-only concurrency control ✅

`SETNX` on `seat-lock:{eventId}:{seatId}` with a 5-minute TTL.

> - Why not `SELECT … FOR UPDATE` on the seat row? What specifically fails when the lock must be held across an external HTTP call to Stripe?
> - Why not optimistic locking (`@Version`) on the booking or seat?
> - Why not rely on the unique index alone and let the second buyer fail at insert time? What does the customer experience lose?
> - Why Redis rather than another coordination service (Postgres advisory locks, ZooKeeper, etcd)?
> - Why a hand-rolled `SETNX` lock rather than Redisson / a Redlock implementation? What are you giving up — watchdog lease extension, reentrancy, multi-node safety?

### 3.2 Lock granularity: `(eventId, seatId)` ✅

> - Why is the event id in the key at all, given a seat belongs to exactly one venue and a booking names both?
> - What breaks if the key were seat-only? What breaks if it were event-only?
> - How would this key design extend to multi-seat bookings (buying four seats together)? Would you need all-or-nothing acquisition, and how would you avoid deadlock between two buyers grabbing overlapping sets in different orders?

### 3.3 TTL of 5 minutes ✅

The lock expires after 5 minutes; the Stripe checkout session expires after ~31 minutes.

> - **These two windows disagree.** A customer can still be on Stripe's payment page after their seat lock has expired — this is precisely the race the refund path exists to clean up. Was that trade deliberate (fail-open: free the seat early, refund the loser) or incidental?
> - What is the argument for keeping them mismatched rather than aligning the TTL to the session lifetime?
> - Why not renew the lock (a watchdog / lease extension) while the session is still open?
> - How was 5 minutes chosen? What signal would tell you it is wrong?

### 3.4 Lua compare-and-delete for release ✅

> - Spell out the interleaving that a plain `DEL` allows and the Lua script prevents.
> - Why store the user id as the lock value rather than a random fencing token? Can a user hold two locks on the same seat for different reasons?
> - What happens if the Lua script returns `0` (release refused)? Is that currently detected, logged, or silently ignored — and should it be?

### 3.5 Partial unique index as the authoritative guarantee ✅

`UNIQUE (event_id, seat_id) WHERE status IN ('PENDING','CONFIRMED')`, replacing the original blanket unique constraint (V5).

> - Why was the blanket `UNIQUE(seat_id, event_id)` untenable once bookings could expire? What broke?
> - Why keep `EXPIRED`/`CANCELLED` rows at all instead of deleting them?
> - Which layer is authoritative — Redis or Postgres — and is that reflected in how a constraint violation is surfaced to the user (currently a generic `409 "A data conflict occured"`)?
> - If Redis were wiped mid-operation, what exactly would users experience?

### 3.6 Failure modes ❓

> - Redis down at booking time: fail the request, or fall through to database-only enforcement? What does the code do today, and is that the right call?
> - Application crashes between `tryLock` and the booking insert — what cleans up? Is the TTL alone sufficient?
> - Application crashes after the Stripe session is created but before responding — the customer never gets the URL. Who reconciles this?
> - Two application instances behind a load balancer: does anything in the current design assume a single instance?

---

## 4. Payments

### 4.1 Stripe Hosted Checkout over a custom payment form ✅

> - What did hosting the payment page on Stripe buy you — PCI scope, card handling, 3DS?
> - What did it cost — redirect UX, branding, the fact that the seat must stay locked across an off-site journey?
> - Would Payment Intents with an embedded Stripe Elements form have made the lock window shorter and more controllable? Why not take that route?

### 4.2 Webhook-driven confirmation over confirming on redirect ✅

A booking becomes `CONFIRMED` only when `checkout.session.completed` arrives — never on the success-redirect request.

> - Why is the browser redirect untrustworthy as a confirmation signal?
> - What does this cost the customer (a window where the success page shows but the booking is still `PENDING`)? How should the UI handle it?
> - The webhook endpoint is `permitAll`. Signature verification is the only authentication — what else would you want before production (IP allowlist, replay-window checks, event-id deduplication table)?

### 4.3 Idempotency strategy ✅

Idempotency keys `idem_{bookingId}` for checkout sessions and `idem_{paymentIntent}` for refunds, plus unique constraints on `stripe_payment_intent_id` and `booking_id`.

> - Stripe guarantees at-least-once webhook delivery. Where is the deduplication actually enforced — the code path, the constraint, or both?
> - `idem_{bookingId}` is stable for the lifetime of a booking. What happens if a booking legitimately needs a second checkout session (customer abandoned, then returned)?
> - Are the webhook handlers idempotent for *every* interleaving, including `expired` arriving after `completed`?

### 4.4 Automatic refund for the lost race ✅

If a payment completes for a booking whose seat is already confirmed under a different payment intent, the system refunds rather than overselling.

> - Why refund automatically rather than queue for manual review or offer the customer an alternative seat?
> - The refund call currently throws a `RuntimeException` on failure inside a `@Transactional` webhook handler. What happens to the transaction, to Stripe's retry, and to the customer's money? Is there a dead-letter path?
> - Should the losing customer be notified? Where would that hook in once notifications exist?

### 4.5 Storing money ✅

Minor units as integers; `amount_cents` widened to `BIGINT` (V4) to match Stripe's `Long`.

> - Why cents-as-integer rather than `NUMERIC`/`BigDecimal`?
> - `Booking.priceCents` is `Integer` while `Payment.amountCents` is `Long`. Should they agree?
> - Currency is hardcoded to AUD in checkout but stored per-payment. Multi-currency: planned, or deliberately out of scope?
> - **The client sends `priceCents` in the booking request.** What stops a customer from booking a $200 seat for 1 cent? Is price server-derived anywhere, and if not, what is the fix?

---

## 5. Authentication & Authorization

### 5.1 Stateless JWT over server-side sessions ✅

15-minute HS256 access tokens, no session store.

> - What drove statelessness — horizontal scaling, a separate frontend origin, something else?
> - The cost of statelessness is that you cannot revoke an access token. Is a 15-minute window an acceptable exposure? What would force a shorter one?
> - Why HS256 with a shared secret rather than RS256 with a key pair? What changes the day a second service must verify these tokens?

### 5.2 Refresh-token rotation with family revocation ✅

Hashed at rest, rotated on every use, reuse of a revoked token revokes the whole `family_id`.

> - Which attack does rotation defeat that a long-lived refresh token does not?
> - Why revoke the entire family rather than just the replayed token?
> - Family revocation runs in `REQUIRES_NEW`. Why is that essential here?
> - The false-positive case: a flaky network causes a client to retry a refresh, its family is revoked, and a legitimate user is logged out. Is that acceptable? How would you soften it (grace window, replay tolerance)?
> - Why SHA-256 rather than BCrypt for the token hash — and why is that fine here but not for passwords?

### 5.3 Refresh token in an HTTP-only cookie, access token in the body ✅

`HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.

> - Why not both in cookies, or both in the body? What threat does the split address (XSS versus CSRF)?
> - CSRF is disabled globally. With a `SameSite=Strict` cookie that only the `/auth` endpoints accept, is that safe? Write down the argument.
> - `Secure` is always set — does that break local development over plain HTTP, and how is that handled?
> - Where should the frontend keep the access token: memory, `localStorage`, or a second cookie? What did you decide and why?

### 5.4 `userId` as a custom claim rather than the JWT subject ✅

`sub` carries the email; `userId` carries the UUID used by every ownership check.

> - Why not make the UUID the subject? What does email-as-subject give you?
> - Every ownership rule reads `authentication.token.claims['userId']` from SpEL strings that the compiler cannot check. Is there a safer way to express these — a custom annotation, a resolved principal object?

### 5.5 Two authorization layers ✅

Path matchers in `SecurityConfig` plus `@PreAuthorize` on controller methods.

> - Is the path layer defence-in-depth, or duplicated logic that will drift?
> - What is the failure mode when someone adds an endpoint and updates only one layer? How would you detect it — a test, a convention, an architecture test?
> - Ownership checks (`@bookingSecurity.isOwner`) hit the database before the method body runs, loading the entity twice. Does that matter?

### 5.6 Roles, not permissions ✅

`USER`, `EVENT_ORGANISER`, `ADMIN`.

> - `EVENT_ORGANISER` can currently write *any* event, not only events they own. Deliberate simplification or a gap?
> - When does a three-role model stop being enough? What is the migration path to per-resource permissions?

---

## 6. Data & Persistence

### 6.1 PostgreSQL ✅

> - Which Postgres-specific features are you actually relying on (partial indexes, `gen_random_uuid()`, `timestamptz`)? How portable does that leave you?

### 6.2 Flyway with `ddl-auto: validate` ✅

> - Why hand-written SQL migrations rather than Hibernate schema generation?
> - What does `validate` catch that nothing else would?
> - `spring.flyway.repair: true` runs on every start. Safe in development — what must change before production?
> - What is the plan for a migration that needs a backfill, or one that must run without downtime?

### 6.3 UUID primary keys ✅

> - Why UUIDs over `BIGSERIAL`? Enumeration resistance, distributed generation, client-side id creation?
> - Random v4 UUIDs fragment B-tree indexes on insert. At what table size does that start to hurt, and would UUIDv7 be worth adopting?

### 6.4 Constraints in the database, not only in code ✅

> - Which invariants did you deliberately push down to the database, and by what rule did you decide?
> - `DataIntegrityViolationException` currently surfaces as a generic `409 "A data conflict occured"`. Should constraint violations be translated into specific, actionable messages?

### 6.5 DTOs and mappers by hand ✅

Every slice has a `Function<Entity, DTO>` mapper bean.

> - Why hand-written mappers rather than MapStruct or projections?
> - `open-in-view: false` means lazy loading outside a transaction fails. How much of the mapper design is driven by that?
> - `XxxSummaryDTO` types live in the package of the aggregate they describe. Was that deliberate, and does it hold up?

### 6.6 Soft state for expired bookings ✅

Expired and cancelled bookings are kept rather than deleted.

> - Is the motivation audit history, the partial index, analytics — or all three?
> - Do these rows ever get archived or pruned? What is the retention story?

---

## 7. API Design

### 7.1 REST with sub-resource endpoints ✅

`/bookings/{id}/event`, `/bookings/{id}/seat`, `/users/{id}/bookings`, …

> - Why separate round-trips instead of embedding related data in the parent response?
> - How many requests does a booking-history page take today? Is that acceptable, and would sparse fieldsets or GraphQL change the answer?

### 7.2 URI versioning (`/api/v1`) ✅

> - Why path versioning over header or media-type versioning?
> - Security matchers use `/api/v*/` wildcards — what happens to authorization when `v2` appears?

### 7.3 Pagination and filtering ✅

1-based `pageNo`, `pageSize`, `sortBy` as `+field`/`-field`, plus JPA `Specification`s for seat filters.

> - Why 1-based paging on the wire over Spring's 0-based default?
> - Endpoints return bare `List<T>` and drop the total count and page metadata. Deliberate, or an omission the frontend will need fixed?
> - `sortBy` is passed to `Sort.by` largely unvalidated — what is the blast radius of an unknown or sensitive field name?
> - Offset paging degrades on large tables. At what point does keyset pagination become necessary?

### 7.4 Uniform error envelope ✅

`{ path, message, statusCode, localDateTime }` from a single `@ControllerAdvice`.

> - Why this shape rather than RFC 7807 `application/problem+json`?
> - `localDateTime` has no timezone while every stored timestamp is `timestamptz`. Should this be an `Instant`?
> - There is no error code or correlation id. How does a user report an error in a way you can trace?

### 7.5 No OpenAPI specification ⏭️

> - Would springdoc pay for itself here — generated client types for the frontend, contract tests?
> - If you add it, does this README's endpoint table become redundant or complementary?

---

## 8. Testing

### 8.1 Integration-first over unit-first ✅

> - What is the argument that unit tests with mocked repositories would not have protected the behaviour that matters here?
> - What is the current cost — full-suite runtime — and at what point does it stop being worth it?
> - What is genuinely untested today? (Concurrent lock contention under real parallelism? Webhook replay ordering? Refresh-token family revocation races?)

### 8.2 Testcontainers over H2 ✅

> - Name the things H2 could not have tested: partial unique indexes, `gen_random_uuid()`, `timestamptz` semantics, …
> - One static container is shared for the whole JVM and tests truncate tables between runs. Why that over a container per class, or transactional rollback?
> - `TestUtility.clearDatabase` must be updated by hand for every new table. How would you make that failure loud instead of silent?

### 8.3 Mocking Stripe, not Redis ✅

> - What is the principle that puts Stripe on the mocked side of the line and Redis on the real side?
> - Would Stripe's test mode or `stripe-mock` give better fidelity than `@MockitoBean`? What would that catch that mocks do not?

### 8.4 CI scope ✅

Tests only, on pull requests to `main`.

> - Should CI also run the linter, a build of the frontend, a dependency/vulnerability scan?
> - Nothing enforces that a schema change ships with a migration, or that a new endpoint has an authorization rule. Could a test enforce those?

---

## 9. Frontend & Delivery ⏭️

> - Why a separate React SPA rather than server-rendered templates?
> - CORS is not yet configured. What origin policy do you want, and how does that interact with the `SameSite=Strict` refresh cookie once the frontend is on a different origin?
> - Stripe success/cancel URLs are hardcoded to `localhost:8181`. Where should they point, and how do they become environment-driven?
> - Where does the frontend hold the access token, and what is the refresh-on-401 strategy?

---

## 10. Deferred Decisions

Things consciously left out, with the conditions that would bring them in.

### 10.1 Kafka async notifications ⏭️
> - What is the first event worth publishing, and who consumes it?
> - Does the booking transaction need an outbox to avoid publishing events for rolled-back work?
> - Why Kafka over a simpler queue, or Spring's in-process events, for this scale?

### 10.2 OAuth2 social login ⏭️
> - The `auth_provider` discriminator and the `chk_password_or_oauth` constraint already anticipate this. What remains?
> - Account linking: what happens when someone registers locally, then signs in with Google using the same email?

### 10.3 AWS S3 image uploads ⏭️
> - Direct browser upload with presigned URLs, or proxied through the API? What decides it?
> - Where do image references live in the data model?

### 10.4 Multi-seat bookings ❓
> - Does the booking model need a parent order entity?
> - How does all-or-nothing locking across several seats avoid deadlock?

### 10.5 Deployment & operations ⏭️
> - Where does this run, and how do secrets get there (they are currently a local `.env`)?
> - Is Redis persistent or ephemeral in that environment? What does losing it mid-sale cost?
> - What must be observable to run this — lock contention rate, webhook lag, refund count, `PENDING` bookings older than their session?
> - `WebhookController` logs full payloads and `AuthController` prints cookies to stdout. What is the logging policy before this touches real customers?
