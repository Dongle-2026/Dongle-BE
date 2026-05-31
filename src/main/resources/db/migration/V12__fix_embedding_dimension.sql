-- nomic-embed-text 모델 기준 768차원으로 수정
ALTER TABLE news DROP COLUMN IF EXISTS embedding;
ALTER TABLE news ADD COLUMN embedding vector(768);

DROP INDEX IF EXISTS idx_news_embedding;
CREATE INDEX idx_news_embedding ON news USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
