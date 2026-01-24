CREATE TABLE IF NOT EXISTS steps (
  workflow_id TEXT NOT NULL,
  step_key TEXT NOT NULL,
  status TEXT NOT NULL,
  output TEXT,
  PRIMARY KEY (workflow_id, step_key)
);
