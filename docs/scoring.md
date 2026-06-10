# Scoring

## MVP Philosophy

The MVP score is empirical and explainable.

The app should compare current conditions to historically logged sessions and infer a likely
score from the nearest similar sessions.

## Recommended MVP Approach

- Compute a vector of aggregated conditions for each saved surf session
- Compute a current conditions vector
- Measure weighted similarity
- Use the nearest historical sessions to estimate a current score

## Candidate Features

- average wind speed
- average wind direction
- average wave height
- average wave period
- average wave direction
- session time of day
- tide water height when available
- tide phase when available
- time distance to high tide when available
- time distance to low tide when available

## Current Weights

Weather and marine features:

- wind speed: `1.4`, scaled over `40 km/h`
- wind gust: `0.6`, scaled over `60 km/h`
- wind direction: `1.2`, scaled over `180°`
- wave height: `2.0`, scaled over `4 m`
- wave period: `1.5`, scaled over `20 s`
- wave direction: `1.0`, scaled over `180°`

Tide features are optional. They are used only when both the current conditions and a historical
session have usable tide data in the local cache:

- water height: `1.2`, scaled over `4 m`
- tide phase: `0.8`, exact match or mismatch between rising/falling
- time to nearest high tide: `0.8`, scaled over `360 min`
- time to nearest low tide: `0.8`, scaled over `360 min`

If tide data is missing on either side, tide features are ignored for that comparison instead of
penalizing the session arbitrarily. The scoring code must never call the tide provider directly;
it reads only canonical tide snapshots derived from the local cache.

## Constraints

- The score must be explainable in the UI
- Use simple weighting first
- Do not introduce opaque machine learning in MVP
