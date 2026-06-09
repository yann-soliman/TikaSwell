# AGENTS.md

## Goal

Build a simple internal web app that helps detect good surf conditions empirically.

The app stores past surf sessions with a time window and a score out of 10, then compares
current conditions to historical sessions to estimate how promising the spot is right now.

## Current Scope

- Single admin user only
- Single initial spot for MVP, with room to add more spots later
- Single data source for MVP: Open-Meteo
- Server-rendered UI only
- SQLite for MVP

## Read This First

Before making non-trivial changes, read these files in order:

1. `PROJECT_BRIEF.md`
2. `docs/product-scope.md`
3. `docs/architecture.md`
4. `docs/data-sources.md`
5. `docs/scoring.md`
6. `docs/decisions.md`

## Chosen Stack

- Kotlin
- Spring Boot
- Spring Data JDBC
- Flyway
- Thymeleaf
- HTMX
- SQLite
- Docker Compose

## Architecture Rules

- Keep the codebase as a modular monolith.
- Prefer explicit, testable services over framework magic.
- Keep business logic independent from the external data provider format.
- Treat `ConditionsProvider` as the main abstraction for weather/marine sources.
- Keep SQLite-specific details inside infrastructure/configuration boundaries.
- Make PostgreSQL migration possible later by avoiding SQLite-specific SQL everywhere.

## Delivery Rules

- Do not introduce a SPA or separate frontend app for the MVP.
- Do not add authentication complexity.
- Do not add alert delivery in MVP.
- Do not add multi-spot support yet, but do not block it structurally.
- Prefer small, reviewable increments with clear acceptance criteria.
- Update docs when an architectural decision changes.

## Data Source Rules

- Open-Meteo is the only provider for MVP.
- Future providers must plug through a provider interface and map into a canonical internal model.
- Do not depend on third-party forecast tools in the MVP implementation.

## Scoring Rules

- The score must remain explainable.
- Start with a simple nearest-neighbor style similarity approach.
- Avoid opaque ML for MVP.
- The UI should eventually be able to explain which past sessions influenced the current score.

## Out Of Scope For MVP

- Multi-user support
- Multi-spot support
- Email or Gotify alerts
- Third-party forecast tool integrations
- Mobile app
- Public API for third parties
