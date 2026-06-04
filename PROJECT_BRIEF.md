# TikaSwell

TikaSwell is an internal tool to log good surf sessions and compare present conditions
to historical sessions rated highly.

The first target is pragmatic and intentionally narrow:

- one user
- one surf spot
- one source of conditions data
- one simple page

The product goal is not to produce a perfect surf forecast. It is to build an empirical memory
of what "felt good" on the spot and reuse that memory when similar conditions appear again.

For MVP:

- the first tracked spot is configured by the deployment
- the source of conditions is Open-Meteo
- a surf session is entered through a form with date, start time, end time, rating, and
  optional notes
- the home page shows current conditions, an estimated score, and the historical sessions

The architecture should stay simple today but leave room for:

- multiple providers later
- multiple spots later
- alerting later
- a future migration from SQLite to PostgreSQL
