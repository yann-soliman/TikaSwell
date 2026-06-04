# Architecture

TikaSwell should remain a modular monolith with explicit boundaries.

## Layers

### domain

- Core business models
- No framework-specific logic
- No direct dependency on Open-Meteo or SQLite

### application

- Use cases
- Score computation orchestration
- Session aggregation logic
- Current conditions presentation logic

### infrastructure

- Open-Meteo provider implementation
- SQLite repositories
- Flyway migrations
- Scheduling
- External provider mapping

### web

- Spring MVC controllers
- Form handling
- Thymeleaf views
- HTMX fragments when needed

## Main Concepts

- `Spot`
- `SurfSession`
- `ConditionSnapshot`
- `CurrentConditions`
- `CurrentScore`
- `ConditionsProvider`

## Architectural Constraints

- The provider output must be mapped to a canonical internal conditions model.
- Do not leak provider DTOs into business logic.
- Keep persistence details out of the domain.
- Keep the scoring algorithm in dedicated services that are easy to replace later.
