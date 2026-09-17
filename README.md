# EventHive — a ticketed-event booking platform with distributed seat locking

EventHive is a backend-intensive, concurrency-focused project built around a classic system-design problem: **two people clicking "buy" on the same seat at the same moment.**

Selling a seat is not a single write. It is a sequence — reserve the seat, hand the customer off to a payment provider, wait for that provider to confirm minutes later — and the seat must belong to exactly one person for the whole duration, across however many application instances are running. EventHive solves this with a **Redis distributed lock** held for the checkout window, a **partial unique index** in Postgres as the last line of defence, and a **webhook-driven confirmation flow** where a booking only ever becomes `CONFIRMED` on Stripe's word, never on the request that started checkout. When the race is lost anyway — the lock expires while the customer is still on Stripe's page — the system detects the duplicate payment and issues an automatic refund rather than overselling the seat.

> 📸 **Screenshot placeholder** — homepage screenshot to be added here.
> Drop the image at `docs/screenshots/homepage.png` and replace this block with:
> `![EventHive homepage](docs/screenshots/homepage.png)`

## Architecture / Design Goals

- **Database** — Postgres 14, accessed through Spring Data JPA with `ddl-auto: validate`. The schema is owned by Flyway migrations, and correctness invariants (one active booking per seat per event, one payment per booking, unique Stripe payment intent) are enforced as database constraints rather than application checks.
- **Backend** — Spring Boot 4 on Java 21, organised as vertical slices by domain (`users`, `venues`, `events`, `seats`, `bookings`, `payments`) with a consistent entity → repository → service → controller → DTO shape per slice.
- **Concurrency** — Redis holds a per-`(event, seat)` lock with a 5-minute TTL, acquired with `SETNX` and released through a Lua compare-and-delete so a lock can only be released by the user who owns it.
- **Payments** — Real Stripe Checkout integration: hosted checkout sessions created with idempotency keys, confirmation and expiry driven by signature-verified webhooks, and automatic refunds for payments that arrive after the seat is already gone.
- **Security** — Stateless JWT access tokens (15 min) plus rotating, hashed, family-tracked refresh tokens in an HTTP-only cookie (7 days), with RBAC over three roles and per-resource ownership checks.
- **Testing** — Integration-first. Repository and web tests run against a real Postgres via Testcontainers; Redis locking is exercised against a real Redis; Stripe is the only mocked boundary.
- **Frontend** — Minimal React 19 + Vite interface, currently in early development.

## Tech Stack

| Layer | Choice |
| --- | --- |
| Language / runtime | Java 21 (Temurin), Gradle |
| Framework | Spring Boot 4.1, Spring Security, Spring Data JPA |
| Datastore | PostgreSQL 14, Flyway migrations |
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

Full backend documentation — API reference, data model, authentication flow, booking and payment lifecycle, configuration and testing — lives in [`backend/README.md`](./backend/README.md). Design decisions and their trade-offs are recorded in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

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

**In progress**
- [ ] React frontend (UX/UI)

**Planned / future improvements**
- [ ] Kafka async notifications (email/notification service via background events)
- [ ] OAuth2 social login (the data model already carries an `auth_provider` discriminator)
- [ ] AWS S3 image uploads for event and venue media
- [ ] Deployment
