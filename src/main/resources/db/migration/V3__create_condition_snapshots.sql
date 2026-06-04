CREATE TABLE condition_snapshots (
  id INTEGER PRIMARY KEY,
  surf_session_id INTEGER NOT NULL,
  spot_id TEXT NOT NULL,
  observed_at TEXT NOT NULL,
  wind_speed_kmh REAL NOT NULL CHECK (wind_speed_kmh >= 0),
  wind_gust_kmh REAL CHECK (wind_gust_kmh IS NULL OR wind_gust_kmh >= 0),
  wind_direction_degrees INTEGER CHECK (wind_direction_degrees IS NULL OR wind_direction_degrees BETWEEN 0 AND 359),
  wave_height_meters REAL CHECK (wave_height_meters IS NULL OR wave_height_meters >= 0),
  wave_period_seconds REAL CHECK (wave_period_seconds IS NULL OR wave_period_seconds >= 0),
  wave_direction_degrees INTEGER CHECK (wave_direction_degrees IS NULL OR wave_direction_degrees BETWEEN 0 AND 359),
  provider_name TEXT NOT NULL,
  FOREIGN KEY (surf_session_id) REFERENCES surf_sessions (id) ON DELETE CASCADE
);

-- Index utilisé par la future agrégation des conditions d'une session.
CREATE INDEX idx_condition_snapshots_session_observed_at
  ON condition_snapshots (surf_session_id, observed_at);

-- Index utilisé par le futur scoring pour relire les conditions historiques du spot.
CREATE INDEX idx_condition_snapshots_spot_observed_at
  ON condition_snapshots (spot_id, observed_at DESC);
