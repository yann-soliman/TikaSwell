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

Stormglass is selected as the MVP tide provider.

Scope:

- tide level
- high and low tide timestamps
- tide station metadata when available
- current and historical tide context needed by saved sessions

Constraints:

- Stormglass is not a replacement for Open-Meteo weather/marine data.
- The free plan is limited to 10 requests per day, so TikaSwell must cache tide data in SQLite.
- The application must never call Stormglass on every dashboard render.
- The API key must be injected through `STORMGLASS_API_KEY` only.
- The real API key must never appear in Git, logs, issues, README examples, or compose files.
- Stormglass is not treated as an official hydrographic authority; local validation remains necessary.

Implementation notes:

- Prefer fetching a compact tide window around the session/current time and caching it by spot/date.
- Store provider metadata separately from the canonical tide fields so the domain stays provider-neutral.
- Keep buoy integrations out of scope until the tide workflow is validated.

References:

- Stormglass Global Tide API: https://stormglass.io/global-tide-api/
- Stormglass pricing: https://stormglass.io/pricing/
