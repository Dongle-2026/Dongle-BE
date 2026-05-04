# 유스레터 백엔드 개발 일정

> 작성: 정다은 / 기준일: 2026-04-09 / 최종 수정: 2026-05-04

---

## 단계별 진행 현황

```
1단계 (기반)     : AUTH ✅  USER ✅  DB 마이그레이션 (수동 init.sql)
2단계 (핵심 MVP) : NEWS ✅  TERM ✅
3단계 (차별화)   : POLICY ✅  SPEND ⬜
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

## 2026-05-04 — WEEK4: Term Dictionary 완성 + Policy 구현

> 브랜치: `feature/week4-term-policy`

### Term Dictionary 완성 (TERM-01~05)

| ID | 기능 | 엔드포인트 | 상태 |
|----|------|-----------|------|
| TERM-01 | 용어 상세 조회 (관련 뉴스 5건, 학습 여부) | GET /api/terms/{termId} | ✅ |
| TERM-02 | 용어 전체 목록 조회 (ltree path 필터) | GET /api/terms | ✅ |
| TERM-03 | 용어 학습 완료 처리 | POST /api/terms/{termId}/learn | ✅ |
| TERM-04 | 내가 학습한 용어 목록 (중복 제거) | GET /api/users/me/learned-terms | ✅ |
| TERM-05 | 용어 전문 검색 (tsvector) | GET /api/terms/search | ✅ |
| TERM-06 | 용어 자동완성 (pg_trgm) | GET /api/terms/autocomplete | ✅ |
| TERM-07 | 카테고리별 용어 조회 (ltree) | GET /api/terms/category | ✅ |
| TERM-08 | 난이도별 용어 조회 | GET /api/terms/level | ✅ |

### Policy 구현 (POLICY-01~05)

| ID | 기능 | 엔드포인트 | 상태 |
|----|------|-----------|------|
| POLICY-01 | 정책 목록 조회 (지역 필터) | GET /api/policies | ✅ |
| POLICY-02 | 정책 상세 조회 | GET /api/policies/{policyId} | ✅ |
| POLICY-03 | 맞춤 정책 추천 (나이/지역/소득 기반) | GET /api/policies/recommend | ✅ |
| POLICY-04 | 정책 즐겨찾기 토글 | POST /api/policies/{policyId}/bookmark | ✅ |
| POLICY-05 | 마감 알림 스케줄러 (D-7, D-3, D-1) | 내부 @Scheduled | ✅ |

### 테스트 추가

| 파일 | 케이스 수 | 설명 |
|------|-----------|------|
| `TermServiceTest` | 12건 | 목록/검색/상세/학습/학습이력 전 경로 |
| `PolicyServiceTest` | 11건 | 목록/추천/상세/북마크 토글 전 경로 |

### 기술 메모

- **Term ltree**: `financial_terms.path` 컬럼 타입이 `ltree`이므로 `CAST(:x AS ltree)` 없이 쓰면 `ERROR: operator does not exist`. 모든 네이티브 쿼리에서 명시적 캐스팅 필수
- **Term tsvector**: `search_vector` 컬럼은 트리거(`trg_terms_search_vector`)가 name + definition + simple_definition을 `simple` 사전으로 자동 생성. 검색 시 `to_tsquery('simple', :query)` 사용 (한국어 토크나이저 없이 단순 토크나이징)
- **Policy QueryDSL**: `QPolicy`는 QueryDSL APT가 `build/generated/querydsl`에 자동 생성. 빌드 전 gradle `compileJava` 먼저 실행 필요
- **Policy 추천 기본 나이**: `User.birthDate`가 null이면 25로 fallback — 정책 추천용 프로필 미입력 신규 가입자 대응
- **PolicyScheduler 중복 방지**: `policy_notifications.existsByUserIdAndPolicyId` 체크로 동일 (user, policy) 조합 중복 저장 방지. `is_sent = false` 상태로 생성 후, 조회 시 `markAsSent()` 호출
- **TermLookupLog 중복 제거**: `getLearnedTerms`에서 같은 termId를 여러 번 조회한 로그가 있을 수 있음. `LinkedHashMap`으로 termId 기준 중복 제거 → 최신 학습 날짜 기준 정렬

---

## 2026-05-04 이후 — 3단계 나머지 + 4~5단계

| 단계 | 내용 |
|------|------|
| 3단계 | SPEND-01~09 (소비 CRUD + 집계 + 트렌드 + 예산 + AI 인사이트) |
| 4단계 | STOCK-01~04 (주식 시세 + 관심종목) |
| 4단계 | NOTI-01~04 (알림 목록/읽음처리) |
| 5단계 | stub → AiServiceClient 실제 연동 (혜린 API 완성 후) |
