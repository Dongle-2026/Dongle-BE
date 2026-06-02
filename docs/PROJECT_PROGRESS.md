# 유스레터 백엔드 — 전체 개발 진행 정리

> 작성: 정다은  
> 최종 수정: 2026-06-02  
> 브랜치: `feature/card-news`

---

## 프로젝트 개요

**서비스명:** 유스레터 (Youth Letter) — 청년 금융 AI 플랫폼  
**목표:** 청년이 경제 뉴스를 쉽게 이해하고, 금융 용어를 학습하고, 정책 혜택을 찾을 수 있도록 돕는 플랫폼

### 전체 시스템 구성

| 서버 | 기술 | 포트 | 담당 |
|------|------|------|------|
| Main API | Spring Boot 3.4.4 / Java 17 | 8080 | 정다은 |
| AI Service | Node.js Express + Ollama | 3000 | 김혜린 |
| Frontend | Next.js 14 | 3001 | 안소윤 |
| Database | PostgreSQL 16 + pgvector + ltree | 15432 (로컬) | 공동 |
| Cache | Redis 7 | 6379 | 공동 |

---

## 구현 완료 기능 전체 목록

### 1단계 — Auth / User (완료)

| 기능 | 엔드포인트 | 비고 |
|------|-----------|------|
| 이메일 회원가입 | POST `/api/auth/signup` | BCrypt, 입력 검증 |
| 이메일 로그인 | POST `/api/auth/login` | JWT 발급 |
| 카카오 OAuth2 | GET `/api/auth/oauth2/kakao` | 로컬 개발 리다이렉트 수정 완료 |
| 구글 OAuth2 | GET `/api/auth/oauth2/google` | |
| 토큰 재발급 | POST `/api/auth/reissue` | Redis RT 검증 |
| 로그아웃 | POST `/api/auth/logout` | RT 삭제 + AT 블랙리스트 |
| 회원 탈퇴 | DELETE `/api/auth/withdraw` | soft delete |
| 내 프로필 조회 | GET `/api/users/me` | |
| 프로필 수정 | PATCH `/api/users/me` | |
| 관심 카테고리 설정 | PUT `/api/users/me/categories` | 다중 선택 |
| 정책 추천 프로필 설정 | PUT `/api/users/me/policy-profile` | 나이/지역/소득 |
| 학습 진행 현황 | GET `/api/users/me/learning-stats` | 읽은 뉴스/학습 용어/연속일 |

**핵심 기술:**
- JWT Access Token 15분 / Refresh Token 7일
- Redis 키 규칙: `RT:{userId}` (RT), `BL:{token}` (블랙리스트)
- Spring Security OAuth2 Client로 카카오/구글 소셜 로그인 처리

---

### 2단계 — News / Term (완료)

| 기능 | 엔드포인트 | 비고 |
|------|-----------|------|
| 뉴스 목록 | GET `/api/news` | 카테고리/날짜/키워드, 페이지네이션 |
| 뉴스 상세 | GET `/api/news/{newsId}` | AI 요약 포함 |
| 뉴스 읽음 처리 | POST `/api/news/{newsId}/read` | |
| 뉴스 북마크 토글 | POST `/api/news/{newsId}/bookmark` | |
| 내 북마크 목록 | GET `/api/users/me/bookmarks` | |
| 오늘의 뉴스 브리핑 | GET `/api/news/today` | 관심 카테고리 우선 12건 |
| 크롤링 스케줄러 | 내부 `@Scheduled` | 6시간마다 자동 실행 |
| 크롤링 수동 트리거 | POST `/api/admin/crawl/trigger` | Admin 권한 필요 |
| 크롤링 상태 조회 | GET `/api/admin/crawl/status` | |
| 용어 전체 목록 | GET `/api/terms` | ltree path 필터, 난이도순 |
| 용어 상세 | GET `/api/terms/{termId}` | 관련 뉴스, 학습 여부 포함 |
| 용어 학습 완료 | POST `/api/terms/{termId}/learn` | |
| 용어 전문 검색 | GET `/api/terms/search?q=` | tsvector GIN 인덱스 |
| 용어 자동완성 | GET `/api/terms/autocomplete?query=` | pg_trgm 유사 매칭 |
| 용어 카테고리 조회 | GET `/api/terms/category?path=` | ltree 하위 경로 |

**핵심 기술:**
- Jsoup으로 한경 금융 뉴스 크롤링
- `source_url UNIQUE` 제약으로 중복 방지
- `financial_terms.search_vector`: PostgreSQL 트리거가 자동 생성 (name + definition + simple_definition)
- ltree 연산자 사용 시 반드시 `CAST(:x AS ltree)` 명시 필요

---

### 3단계 — Policy / Spending (완료)

| 기능 | 엔드포인트 | 비고 |
|------|-----------|------|
| 정책 목록 | GET `/api/policies` | 지역/카테고리 필터 |
| 정책 상세 | GET `/api/policies/{policyId}` | |
| 맞춤 정책 추천 | GET `/api/policies/recommend` | QueryDSL 동적 쿼리 |
| 정책 북마크 토글 | POST `/api/policies/{policyId}/bookmark` | |
| 정책 북마크 목록 | GET `/api/users/me/policy-bookmarks` | |
| 마감 알림 스케줄러 | 내부 `@Scheduled` | D-7, D-3, D-1 알림 생성 |
| 소비 등록 | POST `/api/spending` | CREATED 이벤트 |
| 소비 수정 | PUT `/api/spending/{id}` | UPDATED 이벤트 |
| 소비 삭제 | DELETE `/api/spending/{id}` | DELETED 이벤트 (soft) |
| 월별 소비 목록 | GET `/api/spending?year=&month=` | |
| 소비 대시보드 | GET `/api/spending/dashboard` | 카테고리별 집계 |
| 6개월 트렌드 | GET `/api/spending/trend` | LAG 윈도우 함수 |
| 예산 설정 | POST `/api/spending/budget` | upsert |
| 예산 달성률 | GET `/api/spending/budget/status` | |
| AI 소비 인사이트 | POST `/api/spending/ai-insight` | stub 상태 |

**핵심 기술:**
- Policy 추천: QueryDSL 동적 쿼리, 나이/지역/소득 조건 전부 nullable 처리
- `regionCode IS NULL OR regionCode = ?` 조건 필수 (전국 정책 누락 방지)
- `User.birthDate` null 시 나이 기본값 25 사용
- Spending: Event Sourcing 패턴 적용 (CREATED/UPDATED/DELETED 이벤트 누적)
- 월별 조회: `DISTINCT ON (spending_id) ORDER BY spending_id, created_at DESC`

---

### 4단계 — Stock / Notification (완료)

| 기능 | 엔드포인트 | 비고 |
|------|-----------|------|
| 주가 시세 조회 | GET `/api/stocks/{ticker}` | Redis 5분 캐시, stub |
| 관심종목 추가 | POST `/api/stocks/watchlist` | |
| 관심종목 목록 | GET `/api/stocks/watchlist` | |
| 관심종목 삭제 | DELETE `/api/stocks/watchlist/{ticker}` | |
| 내 알림 목록 | GET `/api/notifications` | 미읽음 우선 |
| 단건 읽음 처리 | PATCH `/api/notifications/{id}/read` | |
| 전체 읽음 처리 | PATCH `/api/notifications/read-all` | |

---

### 5단계 — AI 서비스 연동 (완료)

> 2026-05-29 ~ 2026-06-01 작업

**연동 구조:**
```
한경 크롤러 → NewsProcessingService → AiServiceClient
                                            ↓
                              POST /api/ai/process (Node.js :3000)
                                            ↓
                              Ollama qwen3:8b  → 요약 + NER
                              Ollama nomic-embed-text → 768차원 임베딩
                                            ↓
                              news_summaries (요약)
                              financial_terms + article_term_highlights (용어)
                              news_articles.embedding (벡터)
```

**stub → 실제 연동 전환:**
- `application.yml`에서 `ai-service.use-stub: false`로 변경
- Admin 페이지(`/test.html` → 8. Admin)에서 Stub 토글 버튼으로도 전환 가능

**연동 과정에서 수정한 버그 4건:**

| 버그 | 수정 내용 |
|------|----------|
| 임베딩 차원 불일치 (1536 vs 768) | `AiServiceClient` stub 차원 수정, V12 migration 추가 |
| `modelVersion` 하드코딩 ("stub") | `application.yml` 프로퍼티로 교체 |
| NER 결과 미저장 | `saveTermHighlights()` 추가 — `financial_terms` upsert + `article_term_highlights` 저장 |
| 임베딩 미저장 | `news_articles.embedding vector(768)` 컬럼 추가, `saveEmbedding()` native query 저장 |

**연동 테스트 결과:**
- AI 서비스 직접 호출: summary(3줄) + terms(7개) + embedding(768차원) 정상 반환
- 파이프라인: 37건 저장, 0건 실패

---

### 카드뉴스 기능 (완료 — stub 상태)

| 기능 | 엔드포인트 | 비고 |
|------|-----------|------|
| 카드뉴스 생성 | POST `/api/card-news` | ADMIN 권한, AI 연동 전 stub |
| 카드뉴스 조회 | GET `/api/card-news/{articleId}` | |

**카드 구성 원칙:**
- 한 카드에 핵심 내용 1개
- 제목 카드 → 핵심 이슈 → 수치/변화 → 원인 배경 → 의미/영향 → 용어 해설 순서
- AI Service의 `POST /api/ai/card-news`를 호출해서 생성 (현재 stub)

---

## 현재 stub 상태인 기능 (향후 실제 연동 필요)

| 기능 | 위치 | 내용 |
|------|------|------|
| AI 소비 인사이트 | POST `/api/spending/ai-insight` | 소비 패턴 분석 — AI Service 연동 필요 |
| 주가 실시간 시세 | GET `/api/stocks/{ticker}` | 한국투자증권 or Yahoo Finance API 키 발급 후 연동 |
| 카드뉴스 생성 | POST `/api/card-news` | AI Service `POST /api/ai/card-news` 실제 호출로 교체 |
| Push 알림 발송 | PolicyScheduler 내부 | DB 기록만 하는 상태 — FCM/APNs 연동 필요 |

---

## 알려진 한계 / 개선 필요 사항

| 항목 | 내용 | 우선순위 |
|------|------|---------|
| AI 파이프라인 순차 처리 | 기사 1건당 ~2~3분 (Ollama 로컬 추론). 기사 10건 → 약 20~30분 | 중 |
| 단일 트랜잭션 위험 | `processAll()`이 `@Transactional`로 묶여 있어 후반 기사 실패 시 전체 롤백 가능성 | 중 |
| FinancialTerm path 고정 | AI 추출 용어의 ltree path가 `"finance"` 하나로 고정됨. 카테고리 분류 로직 없음 | 하 |
| 요약 버전 관리 미완성 | 재크롤 시 이전 요약이 만료 처리되지 않아 동일 기사에 유효한 요약 중복 생성 가능 | 하 |
| Flyway 미사용 | DB 스키마를 init.sql 단독으로 관리 중. migration 파일(V12, V13)은 문서 역할만 함 | 하 |

---

## 로컬 개발 환경 실행 순서

```bash
# 1. DB + Redis 시작
docker start youthletter-postgres youthletter-redis

# 2. AI Service 시작 (AI 연동 테스트 시)
cd ~/Desktop/youthletter-ai-service/src
node server.js &

# 3. Spring Boot 시작
cd ~/Desktop/youthletter-backend
./gradlew bootRun --args='--spring.profiles.active=local'

# 4. 테스트 콘솔
open http://localhost:8080/test.html
```

**필요한 Ollama 모델 (최초 1회):**
```bash
ollama pull qwen3:8b
ollama pull nomic-embed-text
```

---

## 연동 확인 방법

`http://localhost:8080/test.html` 접속 후:

1. **로그인** — `test-admin@test.com` / `Test1234!` (Admin 계정)
2. **8. Admin → 크롤링 실행** — `savedCount > 0` 이면 새 기사 처리됨
3. **3. News → 뉴스 상세** — `summary` 필드가 `[STUB]` 아닌 한국어 문장이면 AI 연동 성공
4. **DB 직접 확인:**
   ```sql
   SELECT na.id, LEFT(ns.summary_text, 80), COUNT(ath.id) as terms
   FROM news_articles na
   JOIN news_summaries ns ON ns.article_id = na.id AND ns.valid_to IS NULL
   LEFT JOIN article_term_highlights ath ON ath.article_id = na.id
   WHERE ns.summary_text NOT LIKE '[STUB]%'
   GROUP BY na.id, ns.summary_text
   ORDER BY na.id DESC LIMIT 5;
   ```

---

## 커밋 이력 (주요)

| 커밋 | 내용 |
|------|------|
| `dae5cae` | feat: 카드뉴스 자동 생성 기능 구현 (AI 연동 전 stub 버전) |
| `94ace61` | feat: 카드뉴스 프론트 테스트 페이지 추가 |
| `9a5fec9` | fix: PostgreSQL 이미지 포트 수정 및 카카오 OAuth2 리다이렉트 수정 |
| `bdda743` | fix: 카카오 OAuth2 로컬 개발 리다이렉트 이슈 수정 |
| `fd5bdf4` | feat: test.html에 Admin 유저 역할 관리 카드 추가 |
| `08eea3d` | fix: 임베딩 차원 1536 → 768로 수정 (nomic-embed-text 기준) |
| `19b1f23` | fix: AI 연동 파이프라인 버그 수정 (modelVersion/terms/embedding 저장) |
| `62be194` | docs: AI 서비스 연동 작업 정리 문서 추가 |

---

## 관련 문서

| 문서 | 내용 |
|------|------|
| `docs/AI_SERVICE_INTEGRATION.md` | AI 서비스 연동 구조, 스펙, 설정 가이드 |
| `docs/AI_INTEGRATION_PROGRESS.md` | AI 연동 작업 상세 (버그 발견/수정 과정) |
| `docs/CARD_NEWS_FEATURE.md` | 카드뉴스 기능 기획 및 구현 상세 |
| `docs/KAKAO_LOGIN_FLOW.md` | 카카오 OAuth2 로컬 개발 플로우 |
| `docs/LOCAL_FULLSTACK_SETUP.md` | 로컬 풀스택 환경 세팅 가이드 |
| `docs/DEV_SCHEDULE.md` | 주차별 개발 일정 및 기능 완료 현황 |
