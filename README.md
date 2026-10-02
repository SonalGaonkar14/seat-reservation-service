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

## Live Deployment

The service is deployed on Railway:

https://seat-reservation-service-production.up.railway.app

### Health endpoints

Liveness:

https://seat-reservation-service-production.up.railway.app/actuator/health/liveness

Readiness:

https://seat-reservation-service-production.up.railway.app/actuator/health/readiness

Prometheus metrics:

https://seat-reservation-service-production.up.railway.app/actuator/prometheus

## Authentication used for the take-home

The service derives identity only from the Authorization header. This demo uses opaque bearer tokens so the burst script is simple:

- admin: `Authorization: Bearer admin-secret`
- user 123: `Authorization: Bearer user-123`

A request body cannot choose `userId`. In a production deployment, replace this demo token parser with JWT/OIDC validation and keep the same controller/service contract.

## API

### Create show

```bash
curl -X POST http://localhost:8080/shows \
  -H 'Authorization: Bearer admin-secret' \
  -H 'Content-Type: application/json' \
  -d '{"name":"friday-night","seats":["A1","A2","A3","A4"],"pricePaise":25000}'
```

### Reserve

```bash
curl -X POST http://localhost:8080/shows/<SHOW_ID>/reserve \
  -H 'Authorization: Bearer user-123' \
  -H 'Content-Type: application/json' \
  -d '{"seats":["A1"],"idempotencyKey":"order-123"}'
```

Retrying the same key and same seats returns the original reservation.

Reusing the key with different seats returns `409 Conflict`.

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
6. Multi-seat requests are all-or-nothing: all requested seats must exist and be available, otherwise the transaction rolls back and returns `409 Conflict`.
7. Seat acquisition is sorted, reducing deadlock risk for overlapping multi-seat requests.
8. Money is stored as integer paise (`long`/BIGINT), never floating point.

## Burst / Concurrency Test

The repository contains a Python burst script:

`scripts/burst.py`

The script uses only the Python standard library.

It:

1. Creates a fresh show.
2. Sends concurrent reservation requests for the same hot seat.
3. Uses valid token-derived users.
4. Uses unique idempotency keys.
5. Prints HTTP outcome counts.
6. Checks the final show state.
7. Verifies the reconciliation invariant.

### Run locally

Linux/macOS/Git Bash:

```bash
BASE_URL=http://localhost:8080 COUNT=100 python3 scripts/burst.py
```

For a larger test:

```bash
BASE_URL=http://localhost:8080 COUNT=20000 python3 scripts/burst.py
```

### Windows PowerShell

```powershell
$env:BASE_URL='http://localhost:8080'
$env:COUNT='100'
python scripts/burst.py
```

For 20,000 requests:

```powershell
$env:BASE_URL='http://localhost:8080'
$env:COUNT='20000'
python scripts/burst.py
```

## Live burst test

The deployed Railway service can also be tested directly.

### 100-request test

Linux/macOS/Git Bash:

```bash
BASE_URL=https://seat-reservation-service-production.up.railway.app \
COUNT=100 \
python3 scripts/burst.py
```

Windows PowerShell:

```powershell
$env:BASE_URL='https://seat-reservation-service-production.up.railway.app'
$env:COUNT='100'
python scripts/burst.py
```

### 20,000-request test

Linux/macOS/Git Bash:

```bash
BASE_URL=https://seat-reservation-service-production.up.railway.app \
COUNT=20000 \
python3 scripts/burst.py
```

Windows PowerShell:

```powershell
$env:BASE_URL='https://seat-reservation-service-production.up.railway.app'
$env:COUNT='20000'
python scripts/burst.py
```

For a public/free deployment, it is recommended to start with the 100-request test and increase the request count gradually before running the full 20,000-request burst.

## Expected hot-seat result

For a fresh show where all requests target the same seat and use unique users and idempotency keys:

- Exactly one request should return `201 Created`.
- Remaining reservation attempts should return `409 Conflict`.
- No `5xx` responses should occur.
- The final show state must satisfy:

`available + held + confirmed = totalSeats`

For example, for a fresh 10-seat show:

```text
201 Created : 1
409 Conflict: 19999
5xx Errors  : 0

totalSeats = 10
available  = 9
held       = 0
confirmed  = 1
```

The example above describes the expected behavior; actual results should be taken from the burst test output.

## Metrics

The service exposes Prometheus metrics through:

`GET /actuator/prometheus`

Important metrics include:

- `reservations_confirmed_total`
- `reservations_declined_total{reason="seat-taken"}`
- `reservations_declined_total{reason="per-user-limit"}`
- `reservations_declined_total{reason="idempotent-replay"}`
- `seats_available`

## Health checks

### Liveness

`GET /actuator/health/liveness`

Used to determine whether the application process is running.

### Readiness

`GET /actuator/health/readiness`

Readiness includes the database health check so the application is not considered ready when its database dependency is unavailable.

## Observability

The application uses structured logging with a request/correlation ID.

Example:

```text
2026-10-02 10:20:30.123 INFO [request-id] logger - message
```

This makes it easier to trace requests through the application during concurrent reservation traffic.

## Cancellation

Cancellation is explicit through:

`POST /reservations/{reservationId}/cancel`

The cancellation operation:

1. Locks the reservation.
2. Verifies the authenticated user owns the reservation.
3. Locks the associated seats.
4. Releases confirmed seats.
5. Marks the reservation as cancelled.
6. Executes these changes inside the same database transaction.

The current implementation therefore does not use expiring holds. The `held` count remains `0` unless a future hold workflow is introduced.

## Idempotency

Each reservation request contains an idempotency key.

Example:

```json
{
  "seats": ["A1"],
  "idempotencyKey": "order-123"
}
```

For the same user and show:

### Same key + same request

The original reservation is returned.

### Same key + different seats

The request returns:

`409 Conflict`

with an idempotency conflict response.

This prevents accidental creation of a different reservation when a client retries an existing request with the same key.

## Multi-seat reservation

A reservation can contain multiple seats:

```json
{
  "seats": ["A1", "A2", "A3"],
  "idempotencyKey": "order-456"
}
```

The operation is all-or-nothing.

If all requested seats are available:

`201 Created`

If any requested seat is already unavailable:

`409 Conflict`

The transaction is rolled back, so the request does not partially reserve some seats.

## Per-user reservation limit

The default per-user limit is:

`4 seats`

The limit is configurable through:

`PER_USER_LIMIT`

The authenticated user identity is derived from the bearer token and cannot be supplied through the request body.

This prevents a client from bypassing the limit by changing a `userId` field in the JSON request.

## Database as the source of truth

The database is the source of truth for reservation state.

Concurrency correctness is implemented using database transactions and row-level pessimistic locking rather than Java-level synchronization.

This allows multiple application instances to coordinate through the same database.

## Money representation

Seat prices are represented as integer paise.

Example:

`₹250.00 = 25000 paise`

The API therefore uses:

```json
{
  "pricePaise": 25000
}
```

No floating-point representation is used for monetary values.

## Important production note

For a real payment gateway, the reservation transaction and an external payment call must not be treated as one ACID transaction.

The same idempotency key should be propagated to the payment provider and a durable payment/outbox state should be used.

This exercise has no external payment provider, so the confirmed reservation is the durable charge decision and retries return that same reservation.
