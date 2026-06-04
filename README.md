# TikaSwell

TikaSwell is a Kotlin/Spring Boot app that helps track surf sessions and estimate whether the
current conditions look similar to historically good sessions on a surf spot.

## MVP Scope

- Single admin user
- Single initial spot, configurable by deployment
- Single weather/marine provider: Open-Meteo
- Server-rendered UI with Thymeleaf + HTMX
- SQLite persistence

## Tech Stack

- Kotlin
- Spring Boot
- Spring Data JDBC
- Flyway
- Thymeleaf
- HTMX
- SQLite

## Docs

- [Project Brief](PROJECT_BRIEF.md)
- [Product Scope](docs/product-scope.md)
- [Architecture](docs/architecture.md)
- [Data Sources](docs/data-sources.md)
- [Scoring](docs/scoring.md)
- [Deployment](docs/deployment.md)
- [Decisions](docs/decisions.md)

## Local Run

Once Java 21 is available:

```bash
./gradlew bootRun
```

The app stores its SQLite database under `./data/tikaswell.db` by default.
