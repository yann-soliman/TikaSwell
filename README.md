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

## api-maree.fr Tide Configuration

Open-Meteo remains the main weather/marine provider. api-maree.fr is used only for tide context:
water height and the local tide curve for the configured tide site.

The API key must be provided through an environment variable:

- `API_MAREE_API_KEY`: private api-maree.fr key, set in Portainer or the runtime environment.
  Never put the real key in Git, compose files, README examples, GitHub issues, or logs.
- `API_MAREE_SITE_ID`: tide site identifier, defaults to `saint-nazaire`.
- `API_MAREE_STEP_MINUTES`: tide curve interval in minutes, defaults to `10`.
- `API_MAREE_TIMEZONE`: tide request timezone, defaults to `Europe/Paris`.

Useful cache and prefetch variables:

- `TIKASWELL_CONDITIONS_BACKFILL_ENABLED`: repairs missing historical Open-Meteo snapshots on startup, defaults to `true`.
- `TIKASWELL_CONDITIONS_BACKFILL_DAYS_BEFORE`: historical repair window, defaults to `30`.
- `TIKASWELL_CONDITIONS_BACKFILL_CRON`: daily historical repair cron, defaults to `0 30 3 * * *`.
- `TIKASWELL_TIDE_MAX_PROVIDER_CALLS_PER_DAY`: application-side daily quota, defaults to `120`.
- `TIKASWELL_TIDE_PREFETCH_ENABLED`: enables automatic prefetch, defaults to `true`.
- `TIKASWELL_TIDE_PREFETCH_CRON`: Spring cron for daily prefetch, defaults to `0 0 3 * * *`.
- `TIKASWELL_TIDE_PREFETCH_DAYS_BEFORE`: daily past horizon, defaults to `30`.
- `TIKASWELL_TIDE_PREFETCH_DAYS_AHEAD`: daily future horizon, defaults to `30`.
- `TIKASWELL_TIDE_PREFETCH_STARTUP_DAYS_BEFORE`: startup past horizon, defaults to `30`.
- `TIKASWELL_TIDE_PREFETCH_STARTUP_DAYS_AHEAD`: startup future horizon, defaults to `30`.

The strategy is deliberately conservative: cache-first reads, daily prefetch at 03:00, durable
SQLite cache by spot/date/provider, and no short automatic expiry. Tide data can remain unavailable
when the key is missing, the quota is reached, api-maree.fr is unavailable, or a date has not been
prefetched yet. Buoy data is intentionally out of scope for now. Portainer details are in
[Deployment](docs/deployment.md).

The complete environment variable list, including provider secrets such as `API_MAREE_API_KEY`,
is documented in [Deployment](docs/deployment.md). Real API keys must be set in the runtime
environment, never committed to Git.

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

### Short Example

A past session rated `8/10` was logged with average conditions close to the current conditions:

| Data | Current | Past session |
| --- | ---: | ---: |
| Wind | 24 km/h | 20 km/h |
| Gusts | 34 km/h | 30 km/h |
| Wind direction | 280° | 270° |
| Waves | 1.7 m | 1.5 m |
| Period | 9 s | 10 s |

The app computes a similarity for that session, for example `92 %`.

It does the same comparison with every historical session:

| Session | Rating | Similarity |
| --- | ---: | ---: |
| A | 8/10 | 92 % |
| B | 5/10 | 60 % |
| C | 9/10 | 85 % |

The final score is a similarity-weighted average of the ratings. Here, sessions A and C count more than B, so the estimated score will be close to `8/10`.
