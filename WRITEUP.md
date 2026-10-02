# Design write-up

## Atomic decision
The exact atomic mechanism is a MySQL row lock acquired through JPA `PESSIMISTIC_WRITE` on every requested seat, in sorted seat-number order. The transaction then checks state and changes AVAILABLE to CONFIRMED. A second transaction trying to reserve the same seat blocks until the first commits and then sees CONFIRMED, producing a domain 409 instead of a 500.

For multi-seat requests, all seat rows are locked in deterministic order and the request is all-or-nothing. Deterministic ordering reduces deadlocks caused by overlapping requests.

Per-user concurrency uses a durable `user_show_locks` row. It serializes only concurrent reservations from the same user for the same show, allowing different users to compete on different seats without a global show lock.

## Idempotency
The key is stored in `reservations` with `(show_id,user_id,idempotency_key)` unique. The authenticated user comes from the bearer token and is never accepted from the JSON body. After acquiring the per-user/show lock, the service checks the key. Same key + same normalized seats returns the original reservation; same key + different seats returns 409.

## Holds and release
This implementation chooses explicit cancellation instead of time-boxed holds. A cancellation locks the reservation and its seat rows, verifies ownership, releases the confirmed seats, and marks the reservation CANCELLED in one transaction. This prevents a release from resurrecting a seat incorrectly.

## Consistency vs availability
The MySQL database is the source of truth. During a DB outage the readiness check fails, and reservation operations cannot succeed. This favors correctness/consistency over accepting reservations without the system of record. No cache is used for seat availability decisions.

## Observability
The service exposes liveness/readiness and Prometheus metrics. Structured console logs include a request/correlation ID. At 2am I would page on sustained 5xx, readiness failures, abnormal seat-taken/confirmation ratios, database connection exhaustion, lock/deadlock errors, latency saturation, and a mismatch between API reconciliation and reservation metrics.

## AI usage
AI was used for directed assistance: breaking down the assignment, reviewing concurrency edge cases, drafting API/DTO/entity scaffolding, and generating a load-test harness. The final atomic mechanism, database locking strategy, idempotency model, all-or-nothing semantics, and trade-offs should be understood and explained by the candidate during the interview.

## What I would do next
Add JWT/OIDC validation, Flyway migrations, an outbox for payment/events, a real payment-provider idempotency key, distributed tracing, rate limiting/backpressure, database connection-pool tuning, deadlock retry with bounded backoff, and automated concurrency/invariant tests in CI.
