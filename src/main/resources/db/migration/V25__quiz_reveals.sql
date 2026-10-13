-- "정답 보기" 기록. 정답을 본 문제는 이후 맞혀도 꿀을 주지 않는다.
CREATE TABLE IF NOT EXISTS quiz_reveals (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quiz_id     BIGINT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    revealed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, quiz_id)
);

ALTER TABLE quiz_reveals ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'youthletter_app') THEN
        GRANT SELECT, INSERT, DELETE ON quiz_reveals TO youthletter_app;
        IF NOT EXISTS (SELECT FROM pg_policies WHERE tablename = 'quiz_reveals' AND policyname = 'user_isolation_quiz_reveals') THEN
            CREATE POLICY user_isolation_quiz_reveals ON quiz_reveals TO youthletter_app
                USING (user_id = current_setting('app.current_user_id', true)::bigint);
        END IF;
    END IF;
END $$;
