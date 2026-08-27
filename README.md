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
