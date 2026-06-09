ALTER TABLE condition_snapshots ADD COLUMN wave_peak_period_seconds REAL
  CHECK (wave_peak_period_seconds IS NULL OR wave_peak_period_seconds >= 0);

ALTER TABLE condition_snapshots ADD COLUMN wind_wave_height_meters REAL
  CHECK (wind_wave_height_meters IS NULL OR wind_wave_height_meters >= 0);

ALTER TABLE condition_snapshots ADD COLUMN wind_wave_period_seconds REAL
  CHECK (wind_wave_period_seconds IS NULL OR wind_wave_period_seconds >= 0);

ALTER TABLE condition_snapshots ADD COLUMN wind_wave_peak_period_seconds REAL
  CHECK (wind_wave_peak_period_seconds IS NULL OR wind_wave_peak_period_seconds >= 0);

ALTER TABLE condition_snapshots ADD COLUMN wind_wave_direction_degrees INTEGER
  CHECK (wind_wave_direction_degrees IS NULL OR wind_wave_direction_degrees BETWEEN 0 AND 359);

ALTER TABLE condition_snapshots ADD COLUMN swell_wave_height_meters REAL
  CHECK (swell_wave_height_meters IS NULL OR swell_wave_height_meters >= 0);

ALTER TABLE condition_snapshots ADD COLUMN swell_wave_period_seconds REAL
  CHECK (swell_wave_period_seconds IS NULL OR swell_wave_period_seconds >= 0);

ALTER TABLE condition_snapshots ADD COLUMN swell_wave_peak_period_seconds REAL
  CHECK (swell_wave_peak_period_seconds IS NULL OR swell_wave_peak_period_seconds >= 0);

ALTER TABLE condition_snapshots ADD COLUMN swell_wave_direction_degrees INTEGER
  CHECK (swell_wave_direction_degrees IS NULL OR swell_wave_direction_degrees BETWEEN 0 AND 359);
