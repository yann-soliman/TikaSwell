# Decisions

## 2026-06-04

### Stack

- Kotlin + Spring Boot retained
- Thymeleaf + HTMX retained
- SQLite retained for MVP
- PostgreSQL postponed for later

### Data Source

- Open-Meteo retained as the only MVP provider
- Third-party forecast tools are not retained for the MVP implementation

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

## 2026-06-09

### Tide Provider

- Stormglass is retained as the MVP tide provider.
- Open-Meteo remains the main weather/marine provider for wind, waves, swell, and wind-wave conditions.
- Stormglass is used only to enrich the tide context.
- Tide calls must be cached in SQLite because the free plan quota is very low.
- Provider API keys must be injected through environment variables and must not be logged or committed.
- Buoy integrations remain out of scope for now.
