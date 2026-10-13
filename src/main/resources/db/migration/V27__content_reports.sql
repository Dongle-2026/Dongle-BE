-- 사용자당 같은 퀴즈/카드뉴스 1회 신고. 신고만으로 게시 상태를 변경하지 않는다.
CREATE TABLE IF NOT EXISTS content_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quiz_id BIGINT REFERENCES quizzes(id) ON DELETE CASCADE,
    card_news_id BIGINT REFERENCES card_news(id) ON DELETE CASCADE,
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('INCORRECT_CONTENT', 'AMBIGUOUS_ANSWER', 'OTHER')),
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'REVIEWED', 'DISMISSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((quiz_id IS NOT NULL) <> (card_news_id IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_user_quiz ON content_reports(user_id, quiz_id) WHERE quiz_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_user_card ON content_reports(user_id, card_news_id) WHERE card_news_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_report_status_created ON content_reports(status, created_at DESC, id DESC);
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'youthletter_app') THEN
        GRANT SELECT, INSERT, UPDATE ON content_reports TO youthletter_app;
        GRANT USAGE, SELECT ON SEQUENCE content_reports_id_seq TO youthletter_app;
    END IF;
END $$;
