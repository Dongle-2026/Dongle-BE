# 유스레터 백엔드 개발 일정

> 작성: 정다은 / 기준일: 2026-04-09

---

## 단계별 진행 현황

```
1단계 (기반)     : AUTH ✅  USER ✅  DB 마이그레이션 (수동 init.sql)
2단계 (핵심 MVP) : NEWS 🔧  TERM 🔧
3단계 (차별화)   : POLICY ⬜  SPEND ⬜
4단계 (부가)     : STOCK ⬜  NOTI ⬜  ADMIN ⬜
5단계 (AI 연동)  : stub → 실제 AiServiceClient ⬜
```

---

## 2026-04-07~08 (완료)

| ID | 기능 | 상태 |
|----|------|------|
| AUTH-01 | 이메일 회원가입 | ✅ |
| AUTH-02 | 이메일 로그인 | ✅ |
| AUTH-03 | 카카오 OAuth2 | ✅ |
| AUTH-04 | 구글 OAuth2 | ✅ |
| AUTH-05 | 토큰 재발급 | ✅ |
| AUTH-06 | 로그아웃 | ✅ |
| AUTH-07 | 회원 탈퇴 (soft delete) | ✅ |
| USER-01 | 내 프로필 조회 | ✅ |
| USER-02 | 프로필 수정 (닉네임) | ✅ |
| USER-03 | 관심 카테고리 설정 | ✅ |
| USER-04 | 정책 추천용 프로필 설정 | ✅ |
| USER-05 | 학습 진행 현황 조회 | ✅ |
| - | JSONB @Type 버그 수정 (전 엔티티) | ✅ |
| - | 로컬 API 테스트 콘솔 (test.html) | ✅ |
| - | 크롤링 파이프라인 (HankyungCrawlerService) | ✅ |
| NEWS-01 | 뉴스 목록 조회 (카테고리/페이지네이션) | ✅ |
| NEWS-02 | 뉴스 상세 조회 (stub 요약 포함) | ✅ |
| NEWS-07 | 뉴스 크롤링 스케줄러 (@Scheduled) | ✅ |
| NEWS-08 | 수동 크롤링 트리거 (POST /api/admin/crawl/trigger) | ✅ |
| TERM-01 | 용어 상세 조회 | ✅ |
| TERM-05 | 용어 검색 (tsvector + trgm) | ✅ |

---

## 2026-04-09 (오늘) — 2단계: NEWS + TERM 완성

### 스키마 변경 (DB 재생성 필요)
- `news_bookmarks` 테이블 추가 → `init.sql` 수정
- 재생성: `docker-compose -f docker-compose.dev.yml down -v && up -d`

### 구현 목록

| ID | 기능 | 엔드포인트 | 상태 |
|----|------|-----------|------|
| NEWS-03 | 뉴스 읽음 처리 | POST /api/news/{newsId}/read | ✅ |
| NEWS-04 | 뉴스 북마크 토글 | POST /api/news/{newsId}/bookmark | ✅ |
| NEWS-05 | 북마크 목록 조회 | GET /api/users/me/bookmarks | ✅ |
| NEWS-06 | 오늘의 뉴스 홈 브리핑 | GET /api/news/today | ✅ |
| NEWS-09 | 크롤링 처리 상태 조회 | GET /api/admin/crawl/status | ✅ |
| TERM-02 | 용어 전체 목록 조회 | GET /api/terms | ✅ |
| TERM-03 | 용어 학습 완료 처리 | POST /api/terms/{termId}/learn | ✅ |
| TERM-04 | 내가 학습한 용어 목록 | GET /api/users/me/learned-terms | ✅ |
| - | test.html에 News/Term 테스트 섹션 추가 | - | ✅ |

### 기술 메모
- NEWS-04/05 북마크: `news_bookmarks(user_id, article_id)` UNIQUE, soft-delete 없이 toggle
- NEWS-06 오늘 뉴스: `crawledAt >= 오늘 자정`, 관심 카테고리 우선 정렬, 최대 12건
- NEWS-09 상태 조회: `NewsProcessingService`에 AtomicReference 기반 인메모리 상태 추적
- TERM-03/04: `term_lookup_log` 테이블 재사용 (user_term_learn 테이블은 init.sql에 없음)

---

## 2026-04-10 이후 — 3단계: POLICY + SPEND

| ID | 기능 | 메서드 | 엔드포인트 |
|----|------|--------|-----------|
| POLICY-01 | 정책 목록 조회 | GET | /api/policies |
| POLICY-02 | 정책 상세 조회 | GET | /api/policies/{id} |
| POLICY-03 | 맞춤 정책 추천 | GET | /api/policies/recommend |
| POLICY-04 | 정책 북마크 | POST | /api/policies/{id}/bookmark |
| POLICY-05 | 정책 마감 알림 스케줄러 | 내부 | @Scheduled 09시 |
| POLICY-06 | 정책 데이터 수집 스케줄러 | 내부 | 공공 API |
| SPEND-01~08 | 소비 CRUD + 집계 + 트렌드 + 예산 | 다수 | /api/spending/** |
| SPEND-09 | AI 소비 인사이트 | POST | /api/spending/ai-insight (stub) |

---

## 이후 — 4~5단계

| 단계 | 내용 |
|------|------|
| 4단계 | STOCK-01~04 (주식 시세 + 관심종목) |
| 4단계 | NOTI-01~04 (알림 목록/읽음처리) |
| 5단계 | stub → AiServiceClient 실제 연동 (혜린 API 완성 후) |
