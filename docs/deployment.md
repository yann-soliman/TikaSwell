# Déploiement

## Runtime MVP

Le runtime cible est un LXC dédié qui héberge Docker, piloté via Portainer ou Docker Compose.

## Services

- `app`
- pas de conteneur de base externe pour le MVP

SQLite est stocké dans un volume persistant monté dans le conteneur applicatif.

## Variables d'environnement

Cette section est la référence pour les variables à saisir à la main dans Portainer.
Ne jamais committer de vraie clé API dans le dépôt, même dans un exemple.

| Variable | Obligatoire | Valeur recommandée | Usage |
| --- | --- | --- | --- |
| `TIKASWELL_HTTP_PORT` | Non | `8080` | Port exposé par Docker sur le LXC |
| `SERVER_PORT` | Non | `8080` | Port HTTP interne de Spring Boot |
| `TIKASWELL_DB_PATH` | Non | `/app/data/tikaswell.db` | Chemin SQLite dans le conteneur |
| `TIKASWELL_SPOT_ID` | Oui | `saint-brevin-ermitage` | Identifiant stable du spot |
| `TIKASWELL_SPOT_NAME` | Oui | `Plage de l'Ermitage` | Nom affiché dans l'IHM |
| `TIKASWELL_SPOT_LATITUDE` | Oui | `47.20744` | Latitude du spot |
| `TIKASWELL_SPOT_LONGITUDE` | Oui | `-2.15987` | Longitude du spot |
| `OPEN_METEO_WEATHER_BASE_URL` | Non | `https://api.open-meteo.com` | API météo Open-Meteo |
| `OPEN_METEO_MARINE_BASE_URL` | Non | `https://marine-api.open-meteo.com` | API marine Open-Meteo |
| `STORMGLASS_BASE_URL` | Non | `https://api.stormglass.io` | API Stormglass |
| `STORMGLASS_API_KEY` | Bientôt | valeur privée saisie dans Portainer | Clé API Stormglass, jamais dans Git |

`STORMGLASS_API_KEY` est déjà prévue dans la configuration mais ne sera utilisée qu'à partir
de l'intégration marée. Elle doit rester vide si tu n'as pas encore de clé, ou être saisie
uniquement dans les variables d'environnement de Portainer si tu en as une.

Stormglass est réservé au contexte de marée. Open-Meteo reste la source météo/marine principale.
Le plan gratuit Stormglass est très limité, donc l'intégration devra obligatoirement passer par
un cache SQLite avant d'être affichée dans le dashboard.

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
STORMGLASS_BASE_URL=https://api.stormglass.io
STORMGLASS_API_KEY=
```

Pour `STORMGLASS_API_KEY`, remplacer la valeur vide directement dans Portainer par la vraie
clé privée. Ne pas la mettre dans le compose, le README, une issue GitHub, un commit ou un log.

Après déploiement, tester :

```text
http://IP_DU_LXC_DOCKER:8080
```

Le reverse proxy personnel doit pointer vers ce port HTTP interne.
