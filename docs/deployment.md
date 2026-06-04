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
- `TIKASWELL_DB_PATH`
- `OPEN_METEO_BASE_URL`

## Local Development

Run:

```bash
./gradlew bootRun
```

## Docker

The repository provides a Dockerfile and Compose setup for the MVP deployment path.
