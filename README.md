# Job Service

A Spring Boot REST service for creating, retrieving, and processing jobs.

## Tech Stack

- Java 17
- Spring Boot 3.2.1 (Web, Data JPA, Validation)
- H2 (in-memory database)
- Lombok
- springdoc-openapi (Swagger UI)
- Maven

## Getting Started

### Prerequisites

- JDK 17+
- Maven (or use the included Maven Wrapper)

### Run the application

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

The service starts on `http://localhost:8080` by default.

### Build

```bash
./mvnw clean package
```

### Run tests

```bash
./mvnw test
```

## API

Base path: `/api/jobs`

| Method | Path            | Description                          |
|--------|-----------------|---------------------------------------|
| POST   | `/api/jobs`     | Create a new job                      |
| GET    | `/api/jobs/{id}`| Get a job by ID                       |
| GET    | `/api/jobs`     | List jobs (`status`, `page`, `size`)  |
| POST   | `/api/jobs/process` | Process pending jobs             |

Interactive API docs (Swagger UI) are available at `/swagger-ui.html` once the application is running.

## Concurrency Design: Safe Job Processing

### Problem

`POST /api/jobs/process` can be called concurrently (multiple requests, multiple instances). Without coordination, two callers could both read the same `PENDING` jobs and process them twice.

### Approach

Processing is split into two separate transactions in [`JobServiceImplement.process()`](src/main/java/com/example/jobservice/service/implement/JobServiceImplement.java):

1. **Claim phase (short transaction).** Select all `PENDING` jobs with `SELECT ... FOR UPDATE` (`JobRepository.findByStatusForUpdate`, backed by `@Lock(LockModeType.PESSIMISTIC_WRITE)`), immediately flip them to `PROCESSING`, and commit. The row lock ensures that if two requests race, only one of them can select and claim a given row — the second either blocks until the first commits (after which the row is no longer `PENDING` and is excluded) or, depending on isolation level, sees the row is no longer eligible. Either way, each job is claimed by exactly one request.
2. **Execution phase (separate transaction).** Only the jobs this request actually claimed are processed (with retries) and saved as `COMPLETED` or `FAILED`.

Separating the two phases keeps the pessimistic lock held only for the brief claim step, not for the whole (potentially slow) processing work — so concurrent callers aren't blocked waiting on each other for long-running job execution, only for the quick claim-and-flip.

### Trade-offs

- **Crash after claim, before completion.** If the application crashes after a job is marked `PROCESSING` but before it reaches `COMPLETED`/`FAILED`, that job is stuck in `PROCESSING` forever and will never be picked up again, since the claim query only looks at `PENDING` jobs.
- **Planned mitigation.** A scheduled cleanup job (cron) that finds jobs stuck in `PROCESSING` past a timeout threshold and resets them back to `PENDING` so they get retried on the next processing cycle. This is not yet implemented.
- **Lock contention at scale.** `SELECT ... FOR UPDATE` over all pending jobs is simple and correct, but as the pending queue grows, the claim transaction takes longer and serializes concurrent callers more. An alternative (not implemented here) would be `SKIP LOCKED` semantics or claiming in smaller batches to reduce contention.
