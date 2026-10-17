CREATE TABLE IF NOT EXISTS content_pipeline_runs (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    saved_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);
