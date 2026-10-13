-- 푸시 발송 기록. 같은 사용자·종류·대상·날짜(한국)는 한 번만 보낸다(스케줄 재실행·다중 서버 대비).
CREATE TABLE IF NOT EXISTS push_logs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(30) NOT NULL,
    ref_key    VARCHAR(50) NOT NULL DEFAULT '',
    sent_on    DATE NOT NULL,
    devices    INT NOT NULL DEFAULT 0,          -- 발송 요청한 기기 수
    succeeded  INT NOT NULL DEFAULT 0,          -- Expo가 접수(ok)한 기기 수
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, type, ref_key, sent_on)
);
CREATE INDEX IF NOT EXISTS idx_push_logs_user ON push_logs(user_id, created_at DESC);

ALTER TABLE push_logs ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'youthletter_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON push_logs TO youthletter_app;
        GRANT USAGE, SELECT ON SEQUENCE push_logs_id_seq TO youthletter_app;
        IF NOT EXISTS (SELECT FROM pg_policies WHERE tablename = 'push_logs' AND policyname = 'user_isolation_push_logs') THEN
            CREATE POLICY user_isolation_push_logs ON push_logs TO youthletter_app
                USING (user_id = current_setting('app.current_user_id', true)::bigint);
        END IF;
    END IF;
END $$;
