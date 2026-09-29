-- 자동 적용 아님: 현재 프로젝트는 Flyway 의존성이 없다. 실행 방법은 AI_INTEGRATION_GUIDE.md 참조.
ALTER TABLE card_news ADD COLUMN IF NOT EXISTS model_version VARCHAR(100);
ALTER TABLE card_news ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(100);
ALTER TABLE card_news_cards ADD COLUMN IF NOT EXISTS source_sentence_ids JSONB;
