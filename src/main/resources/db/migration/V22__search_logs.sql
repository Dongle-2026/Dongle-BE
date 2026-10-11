-- 뉴스 검색어 기록(실시간 트렌드 집계용). 로그인 사용자의 첫 페이지 검색만 남기고 30일 후 삭제한다.
CREATE TABLE IF NOT EXISTS search_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    keyword     VARCHAR(50) NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_search_logs_time ON search_logs(searched_at DESC, keyword);
CREATE INDEX IF NOT EXISTS idx_news_articles_category_time ON news_articles(category, published_at DESC);

-- 트렌드는 전체 사용자 집계이므로 사용자 격리(RLS)를 걸지 않는다.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'youthletter_app') THEN
        GRANT SELECT, INSERT, DELETE ON search_logs TO youthletter_app;
        GRANT USAGE, SELECT ON SEQUENCE search_logs_id_seq TO youthletter_app;
    END IF;
END $$;
