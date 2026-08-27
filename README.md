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

## Written Questions

### Question A - System Design

**Prompt:** Suppose this service needs to support 1 million jobs per day and multiple application instances running in parallel. How would you improve the current design for production use?

At this scale, polling a shared table with `SELECT ... FOR UPDATE` from every instance becomes a bottleneck. I would split job processing into two stages connected by a message queue instead of having every instance compete for the same rows.

**Architecture**
- A scheduled cron job (single dispatcher role) periodically loads jobs with status `PENDING`.
- For each job, it publishes a message onto a queue (e.g. SQS/Kafka/RabbitMQ), using the job's ID as an **idempotency key**, then updates the job's status to `PROCESSING` in the same step.
- A pool of worker instances (any number, scaled horizontally) subscribes to the queue. Each message is delivered to exactly one worker, which does the actual job processing.

**Data flow**
1. Cron dispatcher: `PENDING` jobs → published to queue with idempotency key → status set to `PROCESSING`.
2. Workers: consume from the queue → execute the job → report the outcome back (either by updating the DB directly, or by emitting a "result" message that a small consumer turns into a DB update).
3. On success: status → `COMPLETED`.
4. On failure: retried with **exponential backoff, up to 3 attempts**; if still failing, status → `FAILED`.

**Duplicate processing prevention**
- The idempotency key attached to each message lets the queue/broker (or a dedup table keyed by job ID) reject/ignore a message that's already been delivered or processed, so the same job is never executed twice even if the dispatcher or a worker retries.
- Moving jobs to `PROCESSING` at dispatch time means the next dispatcher run never re-selects the same job, even across multiple dispatcher instances (only one instance owns the dispatch role, or dispatch itself is made idempotent the same way).

**Failure handling**
- Worker-side failures use exponential backoff (e.g. 1s, 2s, 4s) for up to 3 retries before giving up.
- If all retries are exhausted, the job is marked `FAILED` and the failure is surfaced (e.g. logs, a dead-letter queue, or an alert) so it can be inspected or manually retried.
- If a worker crashes mid-processing, the message becomes visible again after the queue's visibility timeout and is redelivered to another worker — the idempotency key ensures this doesn't double-apply side effects.

**Scaling approach**
- Workers scale horizontally and independently of the dispatcher — add more instances to increase processing throughput as queue depth grows.
- The dispatcher only needs to run frequently enough to keep the queue fed; it does no heavy processing itself, so it stays lightweight even at 1M jobs/day (~12 jobs/sec average).
- The queue absorbs bursts, decoupling job intake rate from processing rate.

**Operational considerations**
- Monitor queue depth, consumer lag, and per-job processing latency to catch backlogs early.
- Track retry counts and `FAILED` job counts as an alerting signal.
- Because dispatch and processing are decoupled, each can be deployed/restarted independently without losing in-flight work (messages remain in the queue).
