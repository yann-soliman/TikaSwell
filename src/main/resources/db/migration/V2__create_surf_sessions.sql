CREATE TABLE surf_sessions (
  id INTEGER PRIMARY KEY,
  spot_id TEXT NOT NULL,
  starts_at TEXT NOT NULL,
  ends_at TEXT NOT NULL,
  rating INTEGER NOT NULL CHECK (rating >= 0 AND rating <= 10),
  notes TEXT
);

CREATE INDEX idx_surf_sessions_spot_starts_at
  ON surf_sessions (spot_id, starts_at DESC);
