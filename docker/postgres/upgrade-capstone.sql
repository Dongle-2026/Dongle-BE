-- 기존 데이터 보존형 추가 DDL. psql -v ON_ERROR_STOP=1 로 실행.
BEGIN;
ALTER TABLE news_articles ADD COLUMN IF NOT EXISTS embedding vector(768);
CREATE INDEX IF NOT EXISTS idx_articles_embedding ON news_articles USING hnsw (embedding vector_cosine_ops);
CREATE TABLE IF NOT EXISTS card_news (
    id BIGSERIAL PRIMARY KEY, article_id BIGINT NOT NULL REFERENCES news_articles(id),
    total_cards INTEGER NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS card_news_cards (
    id BIGSERIAL PRIMARY KEY, card_news_id BIGINT NOT NULL REFERENCES card_news(id) ON DELETE CASCADE,
    card_order INTEGER NOT NULL, template_type VARCHAR(10) NOT NULL,
    title VARCHAR(50), body VARCHAR(200), sub_body VARCHAR(200), highlight VARCHAR(100), image_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE card_news ADD COLUMN IF NOT EXISTS model_version VARCHAR(100);
ALTER TABLE card_news ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(100);
ALTER TABLE card_news_cards ADD COLUMN IF NOT EXISTS source_sentence_ids JSONB;
CREATE TABLE IF NOT EXISTS device_tokens (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
    device_id VARCHAR(100) NOT NULL, token VARCHAR(2048) NOT NULL, platform VARCHAR(10) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(user_id, device_id)
);
COMMIT;
