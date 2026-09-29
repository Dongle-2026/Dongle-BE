CREATE TABLE IF NOT EXISTS ai_jobs (
    id BIGSERIAL PRIMARY KEY,
    article_id BIGINT NOT NULL REFERENCES news_articles(id),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('ARTICLE', 'CARD')),
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
    attempts INT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36),
    lease_until TIMESTAMPTZ,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_jobs_active ON ai_jobs(article_id, kind)
    WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX IF NOT EXISTS idx_ai_jobs_queue ON ai_jobs(status, available_at, id);
