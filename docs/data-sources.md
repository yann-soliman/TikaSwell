# Data Sources

## MVP Provider

The only provider for MVP is Open-Meteo.

Reasons:

- simple access
- no dependency on scraping
- available historical and forecast data
- good enough to validate the product idea

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
This distinction is important when comparing the application with tools such as Windguru.

## Provider Abstraction

All providers must implement a shared contract, for example:

- fetch current conditions
- fetch historical conditions on a time window
- fetch forecast conditions

That keeps future providers isolated from the rest of the codebase.

## Future Providers

Later candidates may include:

- Windguru if a stable and legitimate integration path exists
- tide-specific providers
- manually imported spot knowledge

Buoy integrations are intentionally left aside for now.
