-- news_articles 테이블에 임베딩 컬럼 추가 (nomic-embed-text 768차원)
ALTER TABLE news_articles ADD COLUMN IF NOT EXISTS embedding vector(768);

CREATE INDEX IF NOT EXISTS idx_articles_embedding
    ON news_articles USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
