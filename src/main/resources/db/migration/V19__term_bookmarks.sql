-- 용어 저장(북마크). 학습 이력(term_lookup_log)과 분리한다.
CREATE TABLE IF NOT EXISTS term_bookmarks (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    term_id    BIGINT NOT NULL REFERENCES financial_terms(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, term_id)
);
CREATE INDEX IF NOT EXISTS idx_term_bookmarks_user ON term_bookmarks(user_id, created_at DESC);

-- news_bookmarks와 같은 사용자 격리 정책(youthletter_app 롤이 있는 환경에서만)
ALTER TABLE term_bookmarks ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'youthletter_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON term_bookmarks TO youthletter_app;
        GRANT USAGE, SELECT ON SEQUENCE term_bookmarks_id_seq TO youthletter_app;
        IF NOT EXISTS (SELECT FROM pg_policies WHERE tablename = 'term_bookmarks' AND policyname = 'user_isolation_term_bookmarks') THEN
            CREATE POLICY user_isolation_term_bookmarks ON term_bookmarks
                TO youthletter_app
                USING (user_id = current_setting('app.current_user_id', true)::bigint);
        END IF;
    END IF;
END $$;
