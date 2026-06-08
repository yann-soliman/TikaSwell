# TikaSwell

[Version française](README.fr.md)

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
- Spring JDBC
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
Override the path with `TIKASWELL_DB_PATH` when running in another environment.

The initial spot is configured through:

- `TIKASWELL_SPOT_ID`
- `TIKASWELL_SPOT_NAME`
- `TIKASWELL_SPOT_LATITUDE`
- `TIKASWELL_SPOT_LONGITUDE`

## Similarity Scoring

TikaSwell uses a simple explainable nearest-neighbor approach.

For each historical session, the app aggregates the captured condition snapshots into a condition vector:

- average wind speed
- average wind gust
- circular average wind direction
- average wave height
- average wave period
- circular average wave direction

The current conditions are converted into the same kind of vector. The app then computes a normalized weighted distance between the current vector and every historical session vector.

Current weights and normalization scales:

| Feature | Scale | Weight |
| --- | ---: | ---: |
| Wind speed | 40 km/h | 1.4 |
| Wind gust | 60 km/h | 0.6 |
| Wind direction | 180 degrees | 1.2 |
| Wave height | 4 m | 2.0 |
| Wave period | 20 s | 1.5 |
| Wave direction | 180 degrees | 1.0 |

Each feature distance is capped at `1.0`, then the weighted average distance is converted to similarity:

```text
similarity = 1 - weighted_distance
```

The app keeps the 5 most similar historical sessions, then estimates the current score as a similarity-weighted average of their ratings. Confidence increases when several similar historical sessions are available.

This is intentionally not machine learning. The goal is to keep the score inspectable and easy to adjust after real sessions reveal better local heuristics.
