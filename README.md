# Seat Reservation at Scale

Java 21 + Spring Boot 3.5.16 + MySQL 8.4. The service implements assigned-seat reservation with database-backed concurrency control, idempotency, per-user limits, cancellation, health checks, Prometheus metrics, structured logs and a concurrency burst script.

## Why this stack
Java 21 is the JDK used in the project. Spring Boot 3.5.16 supports Java 17 through 25. MySQL 8.4 is used as the single source of truth.

## Run locally

Requirements: JDK 21, Maven 3.6.3+, Docker.

### Easiest
```bash
docker compose up --build
```

App: `http://localhost:8080`

Health: `GET /actuator/health/liveness`
Readiness: `GET /actuator/health/readiness`
Metrics: `GET /actuator/prometheus`

### Manual Maven run
Start MySQL with the compose file, then:
```bash
mvn spring-boot:run
```

## Authentication used for the take-home
The service derives identity only from the Authorization header. This demo uses opaque bearer tokens so the burst script is simple:
- admin: `Authorization: Bearer admin-secret`
- user 123: `Authorization: Bearer user-123`

A request body cannot choose `userId`. In a production deployment, replace this demo token parser with JWT/OIDC validation and keep the same controller/service contract.

## API

### Create show
```bash
curl -X POST http://localhost:8080/shows \
  -H 'Authorization: Bearer admin-secret' -H 'Content-Type: application/json' \
  -d '{"name":"friday-night","seats":["A1","A2","A3","A4"],"pricePaise":25000}'
```

### Reserve
```bash
curl -X POST http://localhost:8080/shows/<SHOW_ID>/reserve \
  -H 'Authorization: Bearer user-123' -H 'Content-Type: application/json' \
  -d '{"seats":["A1"],"idempotencyKey":"order-123"}'
```

Retrying the same key and same seats returns the original reservation. Reusing the key with different seats returns 409.

### Show state
```bash
curl http://localhost:8080/shows/<SHOW_ID>
```

### Cancel
```bash
curl -X POST http://localhost:8080/reservations/<RESERVATION_ID>/cancel \
  -H 'Authorization: Bearer user-123'
```

## Concurrency design
1. Each requested seat row is selected with `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) in deterministic seat-number order.
2. A seat is changed from AVAILABLE to CONFIRMED only while its row lock is held. Therefore two transactions cannot confirm the same seat.
3. A unique `(show_id, seat_number)` constraint prevents duplicate seat records.
4. A unique `(show_id, user_id, idempotency_key)` constraint plus a per-user/show lock row makes concurrent retries safe.
5. The per-user/show lock is only for the same user+show, so unrelated users can reserve different seats concurrently.
6. Multi-seat requests are all-or-nothing: all requested seats must exist and be available, otherwise the transaction rolls back and returns 409.
7. Seat acquisition is sorted, reducing deadlock risk for overlapping multi-seat requests.
8. Money is stored as integer paise (`long`/BIGINT), never floating point.

## Burst
Python 3 standard library only:
```bash
BASE_URL=http://localhost:8080 COUNT=20000 python3 scripts/burst.py
```
On Windows PowerShell:
```powershell
$env:BASE_URL='http://localhost:8080'; $env:COUNT='20000'; python scripts/burst.py
```
The script creates a fresh show, sends a hot-seat storm, prints HTTP outcome counts and checks the final reconciliation invariant.

## Metrics
- `reservations_confirmed_total`
- `reservations_declined_total{reason="seat-taken"}`
- `reservations_declined_total{reason="per-user-limit"}`
- `reservations_declined_total{reason="idempotent-replay"}`
- `seats_available`

## Important production note
For a real payment gateway, the reservation transaction and an external payment call must not be treated as one ACID transaction. The same idempotency key should be propagated to the payment provider and a durable payment/outbox state should be used. This exercise has no external payment provider, so the confirmed reservation is the durable charge decision and retries return that same reservation.
