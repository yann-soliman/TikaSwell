# Data Sources

## MVP Provider

The main weather/marine provider for MVP is Open-Meteo.

Reasons:

- simple access
- no dependency on scraping
- available historical and forecast data
- good enough to validate the product idea

Open-Meteo remains responsible for wind, waves, swell, and wind-wave conditions.

## Canonical Internal Model

Provider data should be mapped into an internal model with fields such as:

- timestamp
- wind speed
- wind gust
- wind direction
- wave height
- wave period
- wave peak period
- wave direction
- swell wave height, period, peak period, and direction when available
- wind wave height, period, peak period, and direction when available
- provider name

This model is the input used by the application and scoring layers.

The UI should keep the Open-Meteo marine values explicit: the mean wave period, the peak
period, the swell component, and the wind-wave component are related but not equivalent.
This distinction is important when comparing the application with external forecast tools.

## Provider Abstraction

All providers must implement a shared contract, for example:

- fetch current conditions
- fetch historical conditions on a time window
- fetch forecast conditions

That keeps future providers isolated from the rest of the codebase.

## Future Providers

Later candidates may include:

- additional forecast providers if a stable and legitimate integration path exists
- tide-specific providers
- manually imported spot knowledge

Buoy integrations are intentionally left aside for now.

## Tide Provider

api-maree.fr is selected as the MVP tide provider.

Scope:

- tide water levels for the configured tide site
- local tide curve points used by the UI and scoring
- derived high/low tide timestamps after curve analysis
- current and historical tide context needed by saved sessions

Constraints:

- api-maree.fr is not a replacement for Open-Meteo weather/marine data.
- Tide water levels are stable for a given date and must be cached in SQLite.
- The application must never call api-maree.fr on every dashboard render.
- The API key must be injected through `API_MAREE_API_KEY` only.
- The real API key must never appear in Git, logs, issues, README examples, or compose files.
- api-maree.fr is based on derived Ifremer / PREVIMER harmonic components; local validation remains necessary.

Implementation notes:

- Fetch water levels for the configured tide site, initially `saint-nazaire` for the Ermitage spot.
- Store provider metadata separately from the canonical tide fields so the domain stays provider-neutral.
- Keep buoy integrations out of scope until the tide workflow is validated.

References:

- api-maree.fr documentation: https://api-maree.fr/documentation
- api-maree.fr source and licence notes: https://api-maree.fr/
