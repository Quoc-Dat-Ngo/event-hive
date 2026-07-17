# EventHive -  a ticketed-event booking platform with real-time seat locking

EventHive is a backend-intensive, concurrency-focused comprehensive project designed to handle a classic system-design problem dealing with concurrency, overselling, payments and notifications. 

It solves the problem of double-booking (double purchasing) by utilising Redis distributed lock.

It triggers email/notification service via async events (Kafka), and seamlessly integrated with real payment intent flow (Stripe).

Also using cloud-native tool such as Docker to handle application containerisation, and popular cloud provider (AWS) for storing images and deployment.

## Architecture / Design Goals
- **Database**: Real-time data manipulation within a Postgres database using SQL queries
- **Backend**: Dynamic manipulation and presentation of data through Spring Boot application
- **Frontend**: Minimal, user-friendly ReactJS interface. 
- **Security**: Enhanced the protection with JWT, RBAC, OAuth2 and Redis Rate Limiting.
- **Real-time Updates**: Async background jobs via the integration of Kafka.
- **Payment Integrated**: Support real payment using Stripe. 

## Status

Actively in development — see roadmap below.

- [x] Domain modeling & ERD
- [x] Spring Boot scaffold + dependencies
- [x] Postgres + Docker Compose (dev environment)
- [x] Database schema migrations (Flyway)
- [ ] Core CRUD REST API
- [ ] JWT authentication + RBAC
- [ ] Redis-based seat locking
- [ ] Stripe payment integration
- [ ] Kafka async notifications
- [ ] OAuth2 login
- [ ] AWS S3 image uploads
- [ ] Deployment