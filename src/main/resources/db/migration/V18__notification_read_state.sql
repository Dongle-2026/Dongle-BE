ALTER TABLE policy_notifications ADD COLUMN IF NOT EXISTS is_read BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE policy_notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;
-- 기존 is_sent에는 스케줄러의 가짜 발송과 사용자 읽음이 혼재하므로 자동 역산하지 않는다.
