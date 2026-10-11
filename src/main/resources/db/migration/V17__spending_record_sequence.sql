-- 기존 timestamp 기반 ID와 충돌하지 않도록 기존 최댓값 이후부터 시작한다.
BEGIN;
LOCK TABLE spending_events IN SHARE ROW EXCLUSIVE MODE;
CREATE SEQUENCE IF NOT EXISTS spending_record_id_seq;
SELECT setval('spending_record_id_seq',
    GREATEST((SELECT last_value FROM spending_record_id_seq),
             COALESCE((SELECT max(spending_id) FROM spending_events),0)) + 1, false);
COMMIT;
