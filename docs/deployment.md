# Deployment

## MVP Runtime

The target runtime is a dedicated LXC hosting Docker Compose.

## Services

- `app`
- no external database container for MVP

SQLite is stored on a persistent volume mounted into the app container.

## Environment

Useful variables:

- `SERVER_PORT`
- `TIKASWELL_DB_PATH` defaults to `./data/tikaswell.db` locally and `/app/data/tikaswell.db` in Compose
- `TIKASWELL_SPOT_ID`
- `TIKASWELL_SPOT_NAME`
- `TIKASWELL_SPOT_LATITUDE`
- `TIKASWELL_SPOT_LONGITUDE`
- `OPEN_METEO_WEATHER_BASE_URL`
- `OPEN_METEO_MARINE_BASE_URL`

## Local Development

Run:

```bash
./gradlew bootRun
```

## Docker

The repository provides a Dockerfile and Compose setup for the MVP deployment path.
