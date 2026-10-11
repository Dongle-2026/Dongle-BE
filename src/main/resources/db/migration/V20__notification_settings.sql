-- 사용자별 앱 알림 설정. 행이 없으면 기본값(모두 켜짐)으로 간주한다.
CREATE TABLE IF NOT EXISTS notification_settings (
    user_id       BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    daily_digest  BOOLEAN NOT NULL DEFAULT true,
    quiz_reminder BOOLEAN NOT NULL DEFAULT true,
    streak_alert  BOOLEAN NOT NULL DEFAULT true,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE notification_settings ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'youthletter_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON notification_settings TO youthletter_app;
        IF NOT EXISTS (SELECT FROM pg_policies WHERE tablename = 'notification_settings' AND policyname = 'user_isolation_notification_settings') THEN
            CREATE POLICY user_isolation_notification_settings ON notification_settings
                TO youthletter_app
                USING (user_id = current_setting('app.current_user_id', true)::bigint);
        END IF;
    END IF;
END $$;
