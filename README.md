# FinBase

Spring Boot backend for FinBase.

## Stack

- Java 17
- Spring Boot 3.3.4 (Web, Validation, Data JPA, Actuator)
- PostgreSQL
- Maven

## Prerequisites

- JDK 17
- PostgreSQL running locally (or update `spring.datasource.url` in `application.yml`)

## Running locally

```bash
export DB_USERNAME=finbase
export DB_PASSWORD=finbase
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. Health check: `GET /actuator/health`. Smoke test: `GET /ping`.

## Building

```bash
./mvnw clean package
```

## Tests

```bash
./mvnw test
```
