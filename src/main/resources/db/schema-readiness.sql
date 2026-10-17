-- 읽기 전용. API 접속 계정으로 실행한다. 결과 0행 = 아래 조회 의존성 준비 완료.
-- JPA validate를 대체하지 않으며 RLS 정책/쓰기 권한/데이터 품질은 별도 검증한다.
WITH required(table_name, column_name) AS (
    VALUES
        ('news_articles', 'thumbnail_url'),
        ('quizzes', 'id'), ('quizzes', 'article_id'), ('quizzes', 'quiz_order'),
        ('quizzes', 'question'), ('quizzes', 'choices'),
        ('quiz_attempts', 'id'), ('quiz_attempts', 'user_id'), ('quiz_attempts', 'quiz_id'),
        ('quiz_attempts', 'is_correct'), ('quiz_attempts', 'attempted_at'),
        ('honey_ledger', 'user_id'), ('honey_ledger', 'article_id'), ('honey_ledger', 'quiz_id'),
        ('honey_ledger', 'amount'), ('honey_ledger', 'earned_on'),
        ('quiz_reveals', 'user_id'), ('quiz_reveals', 'quiz_id'),
        ('term_bookmarks', 'user_id'), ('term_bookmarks', 'term_id'),
        ('ai_jobs', 'article_id'), ('ai_jobs', 'kind'), ('ai_jobs', 'status'),
        ('content_reports', 'user_id'), ('content_reports', 'quiz_id'), ('content_reports', 'card_news_id'),
        ('content_reports', 'reason'), ('content_reports', 'description'), ('content_reports', 'status'),
        ('content_reports', 'created_at'), ('content_reports', 'updated_at')
)
SELECT r.table_name, r.column_name,
       CASE WHEN a.attnum IS NULL THEN 'missing' ELSE 'select_denied' END AS problem
FROM required r
LEFT JOIN pg_attribute a ON a.attrelid = to_regclass(r.table_name)
    AND a.attname = r.column_name AND a.attnum > 0 AND NOT a.attisdropped
WHERE a.attnum IS NULL
   OR NOT coalesce(has_column_privilege(a.attrelid, a.attnum, 'SELECT'), false)
ORDER BY r.table_name, r.column_name;
