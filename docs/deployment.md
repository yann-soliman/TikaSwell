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
| `API_MAREE_BASE_URL` | Non | `https://api-maree.fr` | API marée |
| `API_MAREE_API_KEY` | Oui pour la marée | valeur privée saisie dans Portainer | Clé API api-maree.fr, jamais dans Git |
| `API_MAREE_SITE_ID` | Non | `saint-nazaire` | Site de marée utilisé pour le spot |
| `API_MAREE_STEP_MINUTES` | Non | `10` | Pas de temps de la courbe de marée |
| `API_MAREE_TIMEZONE` | Non | `Europe/Paris` | Fuseau horaire des requêtes marée |
| `TIKASWELL_TIDE_MAX_PROVIDER_CALLS_PER_DAY` | Non | `120` | Quota applicatif quotidien pour les appels marée |
| `TIKASWELL_TIDE_PREFETCH_ENABLED` | Non | `true` | Active le préchargement automatique du cache marée |
| `TIKASWELL_TIDE_PREFETCH_CRON` | Non | `0 0 3 * * *` | Horaire du préchargement quotidien Spring, par défaut 03:00 |
| `TIKASWELL_TIDE_PREFETCH_ZONE` | Non | `Europe/Paris` | Fuseau horaire utilisé par le scheduler marée |
| `TIKASWELL_TIDE_PREFETCH_DAYS_AHEAD` | Non | `7` | Horizon quotidien préchargé: `7` signifie aujourd'hui jusqu'à J+7 inclus |
| `TIKASWELL_TIDE_PREFETCH_STARTUP_DAYS_AHEAD` | Non | `1` | Horizon préchargé au démarrage: `1` signifie aujourd'hui et demain |

`API_MAREE_API_KEY` doit être saisie uniquement dans les variables d'environnement de
Portainer. Elle ne doit pas être mise dans le compose, le README, une issue GitHub, un commit
ou un log.

api-maree.fr est réservé au contexte de marée. Open-Meteo reste la source météo/marine principale.
Les hauteurs de marée sont stables pour une date donnée, donc l'application précharge un cache SQLite durable.
Au démarrage, elle précharge seulement aujourd'hui et demain par défaut. Chaque jour à 03:00,
elle complète ensuite la fenêtre glissante jusqu'à J+7, sans récupérer les jours déjà présents
en cache.

## Marée, Cache Et Limites

La marée est lue en cache avant tout appel provider. Le cache est stocké dans SQLite par
`spot/date/provider` et survit aux redémarrages du conteneur tant que le volume Docker est conservé.

Comportement attendu :

- au rendu du dashboard, l'application lit le cache et n'appelle pas api-maree.fr ;
- au calcul du score, l'application lit le cache et n'appelle pas api-maree.fr ;
- au démarrage, le scheduler précharge aujourd'hui et demain par défaut ;
- chaque jour à 03:00, le scheduler complète la fenêtre aujourd'hui -> J+7 ;
- après chaque préchargement, le scheduler tente aussi de backfiller les dates passées des
  sessions enregistrées, dans la limite du quota restant ;
- si une journée est déjà en cache avec des hauteurs exploitables, elle n'est pas refetchée ;
- si un cache est incomplet, par exemple points présents mais hauteurs absentes, il peut être remplacé au prochain préchargement.

États d'indisponibilité possibles dans l'IHM :

- clé api-maree.fr absente ou refusée ;
- quota applicatif atteint pour la journée ;
- provider api-maree.fr indisponible ;
- date pas encore préchargée ;
- cache existant mais incomplet avant réparation.

Limites connues :

- api-maree.fr diffuse des hauteurs indicatives dérivées de composantes harmoniques Ifremer / PREVIMER ;
- le coefficient de marée n'est pas récupéré actuellement ;
- les données peuvent être moins précises qu'une source locale officielle ;
- les bouées sont volontairement laissées de côté pour l'instant ;
- pendant le développement, aucune garantie de rétrocompatibilité SQLite n'est requise : on peut repartir de zéro sur les données si nécessaire.

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
API_MAREE_BASE_URL=https://api-maree.fr
API_MAREE_API_KEY=
API_MAREE_SITE_ID=saint-nazaire
API_MAREE_STEP_MINUTES=10
API_MAREE_TIMEZONE=Europe/Paris
TIKASWELL_TIDE_MAX_PROVIDER_CALLS_PER_DAY=120
TIKASWELL_TIDE_PREFETCH_ENABLED=true
TIKASWELL_TIDE_PREFETCH_CRON=0 0 3 * * *
TIKASWELL_TIDE_PREFETCH_ZONE=Europe/Paris
TIKASWELL_TIDE_PREFETCH_DAYS_AHEAD=7
TIKASWELL_TIDE_PREFETCH_STARTUP_DAYS_AHEAD=1
```

Pour `API_MAREE_API_KEY`, remplacer la valeur vide directement dans Portainer par la vraie
clé privée. Ne pas la mettre dans le compose, le README, une issue GitHub, un commit ou un log.

### Procédure Portainer Pour api-maree.fr

1. Ouvrir la stack TikaSwell dans Portainer.
2. Vérifier que la stack pointe bien vers `ops/docker-compose.yml` sur la branche `main`.
3. Ajouter ou corriger les variables d'environnement :

```text
API_MAREE_API_KEY=<valeur privée saisie uniquement dans Portainer>
API_MAREE_SITE_ID=saint-nazaire
API_MAREE_STEP_MINUTES=10
API_MAREE_TIMEZONE=Europe/Paris
TIKASWELL_TIDE_MAX_PROVIDER_CALLS_PER_DAY=120
TIKASWELL_TIDE_PREFETCH_ENABLED=true
TIKASWELL_TIDE_PREFETCH_CRON=0 0 3 * * *
TIKASWELL_TIDE_PREFETCH_ZONE=Europe/Paris
TIKASWELL_TIDE_PREFETCH_DAYS_AHEAD=7
TIKASWELL_TIDE_PREFETCH_STARTUP_DAYS_AHEAD=1
```

4. Redéployer la stack ou recréer le conteneur applicatif.
5. Vérifier les logs au démarrage : une ligne `Préchargement marée démarrage` doit apparaître.
6. Ouvrir l'IHM et vérifier la carte `Marée` :
   - hauteur d'eau affichée si le cache est complet ;
   - provider affiché `api-maree.fr` ;
   - phase et pleines/basses mers peuvent rester `n/d` tant que la détection depuis la courbe n'est pas livrée ;
   - message français clair si la marée reste indisponible.

Pour un test radical en développement, supprimer le volume SQLite et recréer la stack force un
cache neuf, mais efface aussi les sessions enregistrées.

Après déploiement, tester :

```text
http://IP_DU_LXC_DOCKER:8080
```

Le reverse proxy personnel doit pointer vers ce port HTTP interne.
