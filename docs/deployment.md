# Déploiement

## Runtime MVP

Le runtime cible est un LXC dédié qui héberge Docker, piloté via Portainer ou Docker Compose.

## Services

- `app`
- pas de conteneur de base externe pour le MVP

SQLite est stocké dans un volume persistant monté dans le conteneur applicatif.

## Variables d'environnement

Variables utiles :

- `SERVER_PORT`
- `TIKASWELL_DB_PATH` defaults to `./data/tikaswell.db` locally and `/app/data/tikaswell.db` in Compose
- `TIKASWELL_SPOT_ID`
- `TIKASWELL_SPOT_NAME`
- `TIKASWELL_SPOT_LATITUDE`
- `TIKASWELL_SPOT_LONGITUDE`
- `OPEN_METEO_WEATHER_BASE_URL`
- `OPEN_METEO_MARINE_BASE_URL`

## Développement local

Lancer :

```bash
./gradlew bootRun
```

## Portainer

Créer une stack depuis le dépôt Git :

- Repository URL : `git@github.com:yann-soliman/TikaSwell.git`
- Branch : `main`
- Compose path : `ops/docker-compose.yml`

Variables recommandées pour la plage de l'Ermitage :

```text
TIKASWELL_HTTP_PORT=8080
SERVER_PORT=8080
TIKASWELL_DB_PATH=/app/data/tikaswell.db
TIKASWELL_SPOT_ID=saint-brevin-ermitage
TIKASWELL_SPOT_NAME=Plage de l'Ermitage
TIKASWELL_SPOT_LATITUDE=47.20744
TIKASWELL_SPOT_LONGITUDE=-2.15987
OPEN_METEO_WEATHER_BASE_URL=https://api.open-meteo.com
OPEN_METEO_MARINE_BASE_URL=https://marine-api.open-meteo.com
```

Après déploiement, tester :

```text
http://IP_DU_LXC_DOCKER:8080
```

Le reverse proxy personnel doit pointer vers ce port HTTP interne.
