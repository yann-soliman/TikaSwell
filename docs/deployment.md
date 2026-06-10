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
| `STORMGLASS_API_KEY` | Oui pour la marée | valeur privée saisie dans Portainer | Clé API Stormglass, jamais dans Git |
| `TIKASWELL_TIDE_MAX_PROVIDER_CALLS_PER_DAY` | Non | `6` | Quota applicatif quotidien pour les appels marée |
| `TIKASWELL_TIDE_PREFETCH_ENABLED` | Non | `true` | Active le préchargement automatique du cache marée |
| `TIKASWELL_TIDE_PREFETCH_CRON` | Non | `0 0 3 * * *` | Horaire du préchargement quotidien Spring, par défaut 03:00 |
| `TIKASWELL_TIDE_PREFETCH_ZONE` | Non | `Europe/Paris` | Fuseau horaire utilisé par le scheduler marée |
| `TIKASWELL_TIDE_PREFETCH_DAYS_AHEAD` | Non | `7` | Horizon quotidien préchargé: `7` signifie aujourd'hui jusqu'à J+7 inclus |
| `TIKASWELL_TIDE_PREFETCH_STARTUP_DAYS_AHEAD` | Non | `1` | Horizon préchargé au démarrage: `1` signifie aujourd'hui et demain |

`STORMGLASS_API_KEY` doit être saisie uniquement dans les variables d'environnement de
Portainer. Elle ne doit pas être mise dans le compose, le README, une issue GitHub, un commit
ou un log.

Stormglass est réservé au contexte de marée. Open-Meteo reste la source météo/marine principale.
Le plan gratuit Stormglass est très limité, donc l'application précharge un cache SQLite durable.
Au démarrage, elle précharge seulement aujourd'hui et demain par défaut. Chaque jour à 03:00,
elle complète ensuite la fenêtre glissante jusqu'à J+7, sans récupérer les jours déjà présents
en cache.

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
TIKASWELL_TIDE_MAX_PROVIDER_CALLS_PER_DAY=6
TIKASWELL_TIDE_PREFETCH_ENABLED=true
TIKASWELL_TIDE_PREFETCH_CRON=0 0 3 * * *
TIKASWELL_TIDE_PREFETCH_ZONE=Europe/Paris
TIKASWELL_TIDE_PREFETCH_DAYS_AHEAD=7
TIKASWELL_TIDE_PREFETCH_STARTUP_DAYS_AHEAD=1
```

Pour `STORMGLASS_API_KEY`, remplacer la valeur vide directement dans Portainer par la vraie
clé privée. Ne pas la mettre dans le compose, le README, une issue GitHub, un commit ou un log.

Après déploiement, tester :

```text
http://IP_DU_LXC_DOCKER:8080
```

Le reverse proxy personnel doit pointer vers ce port HTTP interne.
