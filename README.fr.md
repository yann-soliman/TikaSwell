# TikaSwell

[English version](README.md)

TikaSwell est une application Kotlin/Spring Boot pour enregistrer des sessions de surf et estimer si les conditions actuelles ressemblent aux sessions historiques bien notées sur un spot.

## Périmètre MVP

- Un seul administrateur
- Un spot initial configurable au déploiement
- Un seul fournisseur météo/marine : Open-Meteo
- Interface rendue côté serveur avec Thymeleaf + HTMX
- Persistance SQLite

## Stack Technique

- Kotlin
- Spring Boot
- Spring JDBC
- Flyway
- Thymeleaf
- HTMX
- SQLite

## Documentation

- [Brief projet](PROJECT_BRIEF.md)
- [Périmètre produit](docs/product-scope.md)
- [Architecture](docs/architecture.md)
- [Sources de données](docs/data-sources.md)
- [Scoring](docs/scoring.md)
- [Déploiement](docs/deployment.md)
- [Décisions](docs/decisions.md)

## Lancement Local

Avec Java 21 disponible :

```bash
./gradlew bootRun
```

Par défaut, l'application stocke sa base SQLite dans `./data/tikaswell.db`.
Le chemin peut être changé avec `TIKASWELL_DB_PATH`.

Le spot initial se configure avec :

- `TIKASWELL_SPOT_ID`
- `TIKASWELL_SPOT_NAME`
- `TIKASWELL_SPOT_LATITUDE`
- `TIKASWELL_SPOT_LONGITUDE`

La liste complète des variables d'environnement, y compris les secrets provider comme
`STORMGLASS_API_KEY`, est documentée dans [Déploiement](docs/deployment.md). Les vraies clés
API doivent être saisies dans l'environnement d'exécution, jamais dans Git.

## Calcul De Similarité

TikaSwell utilise une approche simple et explicable de type plus proches voisins.

Pour chaque session historique, l'application agrège les snapshots de conditions capturés en un vecteur de conditions :

- vitesse moyenne du vent
- rafale moyenne
- direction moyenne circulaire du vent
- hauteur moyenne des vagues
- période moyenne des vagues
- direction moyenne circulaire des vagues

Les conditions actuelles sont transformées dans le même format. L'application calcule ensuite une distance normalisée et pondérée entre le vecteur actuel et chaque vecteur historique.

Poids et échelles actuels :

| Variable | Échelle | Poids |
| --- | ---: | ---: |
| Vitesse du vent | 40 km/h | 1.4 |
| Rafales | 60 km/h | 0.6 |
| Direction du vent | 180 degrés | 1.2 |
| Hauteur des vagues | 4 m | 2.0 |
| Période des vagues | 20 s | 1.5 |
| Direction des vagues | 180 degrés | 1.0 |

Chaque distance de variable est plafonnée à `1.0`, puis la distance moyenne pondérée est convertie en similarité :

```text
similarité = 1 - distance_pondérée
```

L'application garde les 5 sessions historiques les plus similaires, puis estime le score courant avec une moyenne des notes pondérée par la similarité. La confiance augmente quand plusieurs sessions historiques proches existent.

Ce n'est volontairement pas du machine learning. L'objectif est de garder un score lisible, ajustable, et compréhensible après quelques vraies sessions sur le spot.

### Exemple Court

Une session passée notée `8/10` a été enregistrée avec des conditions moyennes proches des conditions actuelles :

| Donnée | Actuel | Session passée |
| --- | ---: | ---: |
| Vent | 24 km/h | 20 km/h |
| Rafales | 34 km/h | 30 km/h |
| Direction vent | 280° | 270° |
| Vagues | 1,7 m | 1,5 m |
| Période | 9 s | 10 s |

L'application calcule une similarité pour cette session, par exemple `92 %`.

Elle fait la même comparaison avec chaque session historique :

| Session | Note | Similarité |
| --- | ---: | ---: |
| A | 8/10 | 92 % |
| B | 5/10 | 60 % |
| C | 9/10 | 85 % |

Le score final est une moyenne des notes pondérée par la similarité. Ici, les sessions A et C comptent plus que B, donc le score estimé sera proche de `8/10`.
