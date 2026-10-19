# Mini Doodle

A REST API for personal calendar slots and whole-slot meeting bookings, built with Java 21, Spring Boot 4.1.1, Spring Data JPA, and PostgreSQL. Users declare slots as `FREE` or `BUSY`; availability queries include undeclared gaps.

## Run with Docker

Prerequisites: Docker with the Compose plugin, a running Docker engine, and available host ports 8080 and 5432. The image builds the application, so a host Java or Gradle installation is not required. The first build needs internet access to download images, Gradle, and dependencies.

From the repository root:

```bash
docker compose up --build -d
docker compose logs -f app
```

Compose starts PostgreSQL, waits for its health check, and starts the application. Flyway applies the schema migrations during application startup. The API listens at `http://localhost:8080`.

Stop the services while preserving database data:

```bash
docker compose down
```

To start again with existing database data:

```bash
docker compose up
```

To start again with an empty database, remove the named volume. This deletes all application data:

```bash
docker compose down -v
docker compose up --build -d
```

## Run locally and test

Install a Java 21 JDK and set `JAVA_HOME` to it. Use the committed Gradle wrapper; no separate Gradle installation is needed. On Windows, use `gradlew.bat` in place of `./gradlew`.

To run the application on the host with the Compose database:

```bash
docker compose up -d db
./gradlew bootRun
```

If the Compose application is already running, stop it with `docker compose stop app` before using `bootRun`, since both use port 8080.

Local datasource defaults can be overridden with environment variables:

| Variable | Default |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/doodle` |
| `DATABASE_USER` | `doodle` |
| `DATABASE_PASSWORD` | `local-dev-only` |
| `FLYWAY_ENABLED` | `true` |

Keep Flyway enabled for a new database. Hibernate validates the schema and does not create it. Compose sets the equivalent `SPRING_DATASOURCE_*` properties directly.

Run all tests or build the executable JAR:

```bash
./gradlew test
./gradlew bootJar
```

Tests require a running Docker engine because of TestContainers. 

## API documentation and monitoring

Once the application is ready:

| Resource | URL |
| --- | --- |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Liveness | http://localhost:8080/actuator/health/liveness |
| Readiness, including database health | http://localhost:8080/actuator/health/readiness |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |
| Application info | http://localhost:8080/actuator/info |

The info endpoint may return an empty object because no custom info contributors are configured. Prometheus scraping infrastructure is not included.

## Complete API walkthrough

Run the following commands in the same Bash session after startup. They require `curl` and `jq` on the host.

### 1. Create a user

```bash
BASE_URL=http://localhost:8080
USER_EMAIL="demo-$(date +%s)@example.com"
USER_JSON=$(curl --fail-with-body -sS -X POST "$BASE_URL/users" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Alice\",\"email\":\"$USER_EMAIL\"}")
printf '%s\n' "$USER_JSON" | jq .
USER_ID=$(printf '%s' "$USER_JSON" | jq -er '.id')
```

Returns `201 Created` with `id`, `name`, and `email`, plus a `Location` header. A personal calendar is created in the same transaction. Calendar IDs are internal and are never required by API callers. User emails are normalized to lowercase and must be unique.

### 2. Create a free slot

```bash
SLOT_JSON=$(curl --fail-with-body -sS -X POST "$BASE_URL/users/$USER_ID/slots" \
  -H 'Content-Type: application/json' \
  -d '{"start":"2026-11-01T10:00:00Z","end":"2026-11-01T11:00:00Z","status":"FREE"}')
printf '%s\n' "$SLOT_JSON" | jq .
SLOT_ID=$(printf '%s' "$SLOT_JSON" | jq -er '.id')
```

Returns `201 Created`. A slot must have `start < end` and cannot overlap another slot in the same calendar, regardless of status.

### 3. Book the slot

```bash
MEETING_JSON=$(curl --fail-with-body -sS -X POST "$BASE_URL/users/$USER_ID/slots/$SLOT_ID/meeting" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Planning","description":"Discuss next steps","participants":["bob@example.com","BOB@example.com"]}')
printf '%s\n' "$MEETING_JSON" | jq .
MEETING_ID=$(printf '%s' "$MEETING_JSON" | jq -er '.id')

curl --fail-with-body -sS "$BASE_URL/meetings/$MEETING_ID" | jq .
```

Booking returns `201 Created` with a `Location` header and marks the slot `BUSY`. Participant emails are normalized, deduplicated, and returned in sorted order, so this example has one participant. A null or omitted description becomes an empty string. `participants` is required but may be an empty list.

### 4. Query availability

```bash
curl --fail-with-body -sS -G "$BASE_URL/users/$USER_ID/availability" \
  --data-urlencode 'from=2026-11-01T09:00:00Z' \
  --data-urlencode 'to=2026-11-01T12:00:00Z' | jq .
```

The response covers the entire requested window:

```json
{
  "from": "2026-11-01T09:00:00Z",
  "to": "2026-11-01T12:00:00Z",
  "intervals": [
    {"start":"2026-11-01T09:00:00Z","end":"2026-11-01T10:00:00Z","status":"UNDECLARED"},
    {"start":"2026-11-01T10:00:00Z","end":"2026-11-01T11:00:00Z","status":"BUSY"},
    {"start":"2026-11-01T11:00:00Z","end":"2026-11-01T12:00:00Z","status":"UNDECLARED"}
  ]
}
```

To query individual slots instead, optionally filtering by status:

```bash
curl --fail-with-body -sS -G "$BASE_URL/users/$USER_ID/slots" \
  --data-urlencode 'from=2026-11-01T09:00:00Z' \
  --data-urlencode 'to=2026-11-01T12:00:00Z' \
  --data-urlencode 'status=BUSY' | jq .
```

### 5. Cancel the meeting

```bash
curl --fail-with-body -sS -i -X DELETE "$BASE_URL/meetings/$MEETING_ID"

curl --fail-with-body -sS -G "$BASE_URL/users/$USER_ID/availability" \
  --data-urlencode 'from=2026-11-01T09:00:00Z' \
  --data-urlencode 'to=2026-11-01T12:00:00Z' | jq .
```

Cancellation returns `204 No Content`, removes the meeting and its participants, and restores the slot to `FREE`. The 10:00–11:00 interval now reports `FREE`; the surrounding gaps remain `UNDECLARED`. Repeating cancellation returns `404`.

### 6. Modify or delete the unbooked slot

```bash
curl --fail-with-body -sS -X PATCH "$BASE_URL/users/$USER_ID/slots/$SLOT_ID" \
  -H 'Content-Type: application/json' \
  -d '{"status":"BUSY"}' | jq .

curl --fail-with-body -sS -i -X DELETE "$BASE_URL/users/$USER_ID/slots/$SLOT_ID"
```

PATCH accepts any subset of `start`, `end`, and `status`; omitted or null fields retain their existing values. Cancel a meeting before editing or deleting its slot.

## API semantics and errors

- **Intervals are half-open**: `[start, end)`. A slot ending at 11:00 and another starting at 11:00 do not overlap.
- **Availability**: Availability clips slots to the query window, fills gaps with `UNDECLARED`, and merges adjacent intervals with the same state. An empty calendar returns one `UNDECLARED` interval covering the window.
- **Undeclared slots**: `UNDECLARED` means no slot was declared; it does not mean free. It is a response state, not a stored slot status.
- **Range queries**: Query windows must have `from < to` and span at most 366 days. Slot and query timestamps support at most microsecond precision, matching PostgreSQL storage.
- **Validation**: Validation failures return `400`, missing resources or slots outside the specified user's calendar return `404`, and booking, overlap, or database conflicts return `409`.

## Design decisions

### Persistence

Flyway owns the schema in `src/main/resources/db/migration/V1__scheduling.sql`. 

### Schema definition

JPA entities map users, calendars, slots, meetings, and participants. Participant identity is the composite `(meeting_id, email)`. Participants are persisted with the meeting through cascading; cancellation uses a row-counting native delete and the database's `ON DELETE CASCADE` to remove participant rows. 

### Transactions and concurrency

- User and calendar creation is atomic. Meeting creation, participant insertion, and the change to `BUSY` are also one transaction. Cancellation and the change back to `FREE` commit together.
- Booking, slot editing, slot deletion, and meeting cancellation acquire the same slot row lock using `PESSIMISTIC_WRITE`. A transaction holds that lock through validation and mutation, until commit or rollback. Changes to a managed slot are written by JPA dirty checking.
- Every meeting mutation locks the slot before changing the meeting, keeping lock order consistent. Booking rechecks the slot state and existing meeting after acquiring the lock.
- Cancellation first discovers the immutable slot reference, then locks that slot and checks the affected meeting-delete count. If another cancellation already removed the meeting, it returns `404` before changing slot status. A stale cancellation therefore cannot free a slot belonging to a replacement booking.
- Explicit flushes surface relevant database constraints during service operations. A flush does not commit: later failures still roll back the transaction.
- Meeting reads use a read-only, repeatable-read transaction to maintain a consistent view of meeting data. Lock contention uses a five-second PostgreSQL lock timeout and is reported as a conflict by the shared handler when translated to a locking failure.

The integration tests use real PostgreSQL rather than an in-memory substitute because range exclusion constraints, row locks, and timestamp behavior are central to correctness.

## Known limitations and potential extensions

- No authentication, tenant isolation, invitation delivery, or participant acceptance workflow. Add identity-based authorization before exposing this as a shared service.
- No recurring meetings, partial-slot booking, meeting editing, or multi-calendar availability intersection.
- No pagination or result-count limit beyond the 366-day query-window limit; dense calendars may return large responses.
- No idempotency key for booking retries and no automatic retry policy for lock conflicts. Clients should inspect resource state after an uncertain request outcome.
- Cancellation deletes meeting data; there is no cancellation history or audit trail.
- Compose provides local development infrastructure, not production secrets, TLS, database backups, or an observability stack.
