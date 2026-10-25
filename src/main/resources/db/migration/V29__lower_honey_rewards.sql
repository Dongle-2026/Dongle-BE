-- 꿀 지급량 조정: 퀴즈 첫 정답 10 → 3, 기사 읽음 5 → 1.
-- 하루 10꿀이면 히트맵 최고 단계가 되도록 맞춘 값이다. 이미 쌓인 기록도 같은 기준으로 바꾼다.
UPDATE honey_ledger SET amount = 3 WHERE reason = 'QUIZ_CORRECT' AND amount = 10;
UPDATE honey_ledger SET amount = 1 WHERE reason = 'ARTICLE_READ' AND amount = 5;
