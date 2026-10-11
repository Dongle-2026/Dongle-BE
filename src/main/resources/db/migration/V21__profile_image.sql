-- 앱 프로필: 소개글/기본 아바타 + DB 저장 프로필 사진(사용자당 1장)
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_id VARCHAR(30);

CREATE TABLE IF NOT EXISTS profile_images (
    user_id      BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    image_key    UUID NOT NULL UNIQUE,          -- 공개 URL 경로. 업로드마다 새 값(캐시 무효화)
    content      BYTEA NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    byte_size    INTEGER NOT NULL,
    width        INTEGER,
    height       INTEGER,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
