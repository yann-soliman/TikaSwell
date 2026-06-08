# Decisions

## 2026-06-04

### Stack

- Kotlin + Spring Boot retained
- Thymeleaf + HTMX retained
- SQLite retained for MVP
- PostgreSQL postponed for later

### Data Source

- Open-Meteo retained as the only MVP provider
- Windguru not retained for the MVP implementation

### Persistence

- Flyway retained for schema migrations
- Spring Data JDBC retained over JPA for a simpler persistence model

## 2026-06-08

### Persistence

- Spring JDBC is used directly for SQLite repositories.
- Spring Data JDBC is postponed because SQLite dialect auto-configuration is not available in the current setup.
- Repository interfaces remain in the application layer so a later switch to Spring Data JDBC or PostgreSQL stays possible.

### Product Scope

- single user
- single spot
- simple form-based session entry
- explainable empirical score
