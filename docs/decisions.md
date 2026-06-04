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

### Product Scope

- single user
- single spot
- simple form-based session entry
- explainable empirical score
