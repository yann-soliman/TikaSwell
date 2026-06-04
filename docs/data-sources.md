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
- wave direction
- provider name

This model is the input used by the application and scoring layers.

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
