-- AI 생성 퀴즈, 풀이 기록, 꿀(포인트) 원장
ALTER TABLE ai_jobs DROP CONSTRAINT IF EXISTS ai_jobs_kind_check;
ALTER TABLE ai_jobs ADD CONSTRAINT ai_jobs_kind_check CHECK (kind IN ('ARTICLE', 'CARD', 'QUIZ'));

CREATE TABLE IF NOT EXISTS quizzes (
    id                  BIGSERIAL PRIMARY KEY,
    article_id          BIGINT NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    quiz_order          SMALLINT NOT NULL,
    question            VARCHAR(200) NOT NULL,
    choices             JSONB NOT NULL,                 -- ["보기1","보기2","보기3","보기4"]
    answer_index        SMALLINT NOT NULL CHECK (answer_index BETWEEN 0 AND 3),
    explanation         VARCHAR(300) NOT NULL,
    source_sentence_ids JSONB NOT NULL,
    model_version       VARCHAR(50),
    prompt_version      VARCHAR(30),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (article_id, quiz_order)
);

-- 재도전 허용: 모든 제출을 남긴다.
CREATE TABLE IF NOT EXISTS quiz_attempts (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quiz_id        BIGINT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    selected_index SMALLINT NOT NULL CHECK (selected_index BETWEEN 0 AND 3),
    is_correct     BOOLEAN NOT NULL,
    attempted_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_quiz_attempts_user ON quiz_attempts(user_id, quiz_id, attempted_at);

-- 꿀 지급 원장(추가만 함). 지급 규칙의 중복 방지는 부분 유니크 인덱스로 보장한다.
-- QUIZ_CORRECT: 문제당 첫 정답 1회 10꿀 / ARTICLE_READ: 기사당 한국 날짜 하루 1회 5꿀
CREATE TABLE IF NOT EXISTS honey_ledger (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason     VARCHAR(20) NOT NULL CHECK (reason IN ('QUIZ_CORRECT', 'ARTICLE_READ')),
    article_id BIGINT REFERENCES news_articles(id) ON DELETE SET NULL,
    quiz_id    BIGINT REFERENCES quizzes(id) ON DELETE SET NULL,
    amount     INT NOT NULL CHECK (amount > 0),
    earned_on  DATE NOT NULL,                          -- Asia/Seoul 기준 날짜
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_honey_quiz ON honey_ledger(user_id, quiz_id) WHERE reason = 'QUIZ_CORRECT';
CREATE UNIQUE INDEX IF NOT EXISTS uq_honey_read ON honey_ledger(user_id, article_id, earned_on) WHERE reason = 'ARTICLE_READ';
CREATE INDEX IF NOT EXISTS idx_honey_user_day ON honey_ledger(user_id, earned_on);

ALTER TABLE quiz_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE honey_ledger ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'youthletter_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON quizzes, quiz_attempts, honey_ledger TO youthletter_app;
        GRANT USAGE, SELECT ON SEQUENCE quizzes_id_seq, quiz_attempts_id_seq, honey_ledger_id_seq TO youthletter_app;
        IF NOT EXISTS (SELECT FROM pg_policies WHERE tablename = 'quiz_attempts' AND policyname = 'user_isolation_quiz_attempts') THEN
            CREATE POLICY user_isolation_quiz_attempts ON quiz_attempts TO youthletter_app
                USING (user_id = current_setting('app.current_user_id', true)::bigint);
        END IF;
        IF NOT EXISTS (SELECT FROM pg_policies WHERE tablename = 'honey_ledger' AND policyname = 'user_isolation_honey_ledger') THEN
            CREATE POLICY user_isolation_honey_ledger ON honey_ledger TO youthletter_app
                USING (user_id = current_setting('app.current_user_id', true)::bigint);
        END IF;
    END IF;
END $$;
