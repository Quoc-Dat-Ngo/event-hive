# EventHive — a ticketed-event booking platform with distributed seat locking

EventHive is a backend-intensive, concurrency-focused project built around a classic system-design problem: **two people clicking "buy" on the same seat at the same moment.**

Selling a seat is not a single write. It is a sequence — reserve the seat, hand the customer off to a payment provider, wait for that provider to confirm minutes later — and the seat must belong to exactly one person for the whole duration, across however many application instances are running. EventHive solves this with a **Redis distributed lock** held for the checkout window, a **partial unique index** in Postgres as the last line of defence, and a **webhook-driven confirmation flow** where a booking only ever becomes `CONFIRMED` on Stripe's word, never on the request that started checkout. When the race is lost anyway — a payment lands for a seat that is no longer held — the system detects it and issues an automatic refund rather than overselling the seat.

The interesting cases are the ones either side of the happy path. A customer who closes the Stripe tab is handed **their own still-open session** back instead of being told their seat is taken. A booking cancelled while unpaid has its session **expired on Stripe's side** so it cannot be paid afterwards; one cancelled after payment is **refunded inside the same transaction as the status change**, so a failed refund rolls back rather than leaving a cancelled ticket that was never repaid. And a `PENDING` booking whose expiry webhook never arrives is cleaned up by the next customer who asks for the seat.

> 📸 **Screenshot placeholder** — homepage screenshot to be added here.
> Drop the image at `docs/screenshots/homepage.png` and replace this block with:
> `![EventHive homepage](docs/screenshots/homepage.png)`

## Architecture / Design Goals

- **Database** — Postgres 14, accessed through Spring Data JPA with `ddl-auto: validate`. The schema is owned by Flyway migrations, and correctness invariants (one active booking per seat per event, one payment per booking, unique Stripe payment intent) are enforced as database constraints rather than application checks.
- **Backend** — Spring Boot 4 on Java 21, organised as vertical slices by domain (`users`, `venues`, `events`, `seats`, `tiers`, `bookings`, `payments`) with a consistent entity → repository → service → controller → DTO shape per slice.
- **Concurrency** — Redis holds a per-`(event, seat)` lock for a 31-minute hold that matches the Stripe session's own lifetime, acquired with `SETNX` and released through a Lua compare-and-delete so a lock can only be released by the user who owns it.
- **Payments** — Real Stripe Checkout integration: hosted checkout sessions created with idempotency keys, resumable while still open, confirmation and expiry driven by signature-verified webhooks, cancellation that expires or refunds depending on state, and automatic refunds for payments that arrive after the seat is already gone.
- **Pricing** — Customers never send a price. Organisers define price tiers over seat ranges, a seat without a tier is not on sale, and a booking copies the price it was sold at so later tier changes cannot rewrite history.
- **Security** — Stateless JWT access tokens (15 min) plus rotating, hashed, family-tracked refresh tokens in an HTTP-only cookie (7 days), with RBAC over three roles and per-resource ownership checks.
- **Testing** — Integration-first. Repository and web tests run against a real Postgres via Testcontainers; Redis locking is exercised against a real Redis; Stripe is the only mocked boundary.
- **Frontend** — A minimal React 19 + Vite SPA, built through an agentic workflow with Claude Code against the documented API contract. Its job is to exercise the backend end to end — seat map, Stripe redirect, return, cancellation — rather than to be a product of its own.

## Tech Stack

| Layer | Choice |
| --- | --- |
| Language / runtime | Java 21 (Temurin), Gradle |
| Framework | Spring Boot 4.1, Spring Security, Spring Data JPA |
| Datastore | PostgreSQL 14 (dev Compose; tests run 16), Flyway migrations |
| Cache / coordination | Redis 7 |
| Payments | Stripe Java SDK (Checkout + Webhooks + Refunds) |
| Testing | JUnit 5, Testcontainers, Spring Security Test, Mockito |
| Frontend | React 19, Vite, oxlint |
| Infrastructure | Docker Compose (dev), GitHub Actions (CI) |

## Getting Started

```bash
# 1. Start Postgres (:5333) and Redis (:6377)
docker compose up -d

# 2. Create backend/.env with the required secrets
#    (full list and format in backend/README.md → Configuration)

# 3. Run the API on :8181
cd backend && ./gradlew bootRun

# 4. Run the frontend dev server
cd frontend && npm install && npm run dev
```

> The Vite dev server is pinned to `5173` because `eventhive.frontend-url` in `application.yaml` is both the only allowed CORS origin and the base of Stripe's success/cancel redirects.

Full backend documentation — API reference, data model, authentication flow, seat locking, pricing, the booking and payment lifecycle, configuration and testing — lives in [`backend/README.md`](./backend/README.md). The frontend and how it was built are covered in [`frontend/README.md`](./frontend/README.md). Design decisions and their trade-offs are recorded in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Status

Actively in development.

**Done**
- [x] Domain modeling & ERD
- [x] Spring Boot scaffold + dependencies
- [x] Postgres + Docker Compose (dev environment)
- [x] Database schema migrations (Flyway)
- [x] Core CRUD REST API
- [x] Entity repository + integration tests
- [x] CI workflow (GitHub Actions)
- [x] JWT authentication, refresh-token rotation + RBAC
- [x] Redis-based distributed seat locking
- [x] Stripe payment integration (hosted checkout, webhooks, refunds)
- [x] Organiser-defined price tiers + per-event seat map
- [x] Resumable checkout and customer-initiated cancellation
- [x] React frontend (UX/UI), built via an agentic workflow with Claude Code

**In Progress**
- [ ] Deployment via AWS EC2, AWS RDS


**Planned / future improvements**
- [ ] Kafka async notifications (email/notification service via background events)
- [ ] OAuth2 social login (the data model already carries an `auth_provider` discriminator)
- [ ] AWS S3 image uploads for event and venue media
