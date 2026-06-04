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
- future tide-related features when available

## Constraints

- The score must be explainable in the UI
- Use simple weighting first
- Do not introduce opaque machine learning in MVP
