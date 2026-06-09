CREATE TABLE tide_day_cache (
  id INTEGER PRIMARY KEY,
  spot_id TEXT NOT NULL,
  tide_date TEXT NOT NULL,
  provider_name TEXT NOT NULL,
  fetched_at TEXT NOT NULL,
  station_name TEXT,
  station_distance_kilometers REAL
    CHECK (station_distance_kilometers IS NULL OR station_distance_kilometers >= 0),
  coefficient REAL
    CHECK (coefficient IS NULL OR coefficient BETWEEN 0 AND 120),
  unavailable_reason TEXT
    CHECK (
      unavailable_reason IS NULL
      OR unavailable_reason IN ('CACHE_MISS', 'MISSING_API_KEY', 'QUOTA_REACHED', 'PROVIDER_UNAVAILABLE')
    ),
  unavailable_message TEXT,
  UNIQUE (spot_id, tide_date, provider_name)
);

CREATE TABLE tide_points (
  id INTEGER PRIMARY KEY,
  tide_day_cache_id INTEGER NOT NULL,
  observed_at TEXT NOT NULL,
  water_height_meters REAL,
  FOREIGN KEY (tide_day_cache_id) REFERENCES tide_day_cache (id) ON DELETE CASCADE,
  UNIQUE (tide_day_cache_id, observed_at)
);

CREATE TABLE tide_events (
  id INTEGER PRIMARY KEY,
  tide_day_cache_id INTEGER NOT NULL,
  event_type TEXT NOT NULL CHECK (event_type IN ('HIGH', 'LOW')),
  occurs_at TEXT NOT NULL,
  water_height_meters REAL,
  FOREIGN KEY (tide_day_cache_id) REFERENCES tide_day_cache (id) ON DELETE CASCADE,
  UNIQUE (tide_day_cache_id, event_type, occurs_at)
);

CREATE TABLE provider_call_log (
  id INTEGER PRIMARY KEY,
  provider_name TEXT NOT NULL,
  spot_id TEXT,
  called_for_date TEXT,
  called_at TEXT NOT NULL,
  purpose TEXT NOT NULL CHECK (purpose IN ('TIDE_CACHE_PREFETCH', 'TIDE_CACHE_MISS', 'MANUAL_REFRESH')),
  result TEXT NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE', 'SKIPPED')),
  message TEXT
);

CREATE INDEX idx_tide_day_cache_spot_date_provider
  ON tide_day_cache (spot_id, tide_date, provider_name);

CREATE INDEX idx_provider_call_log_provider_called_at
  ON provider_call_log (provider_name, called_at);
