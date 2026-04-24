# DEV_SCHEDULE.md — 백엔드 일별 개발 계획

> 총 기간: 10주 (50일)  
> 기준: 평일 기준, 하루 4~6시간 개발  
> 완료 시 체크박스 체크: `[ ]` → `[x]`

---

## WEEK 1 — 프로젝트 기반 세팅 ✅ 완료

> **완료일: 2026-04-07~08**  
> **설계 변경:** Flyway 대신 `docker/postgres/init.sql` 방식으로 스키마 관리 (pgvector/ltree 같은 확장 타입을 Flyway로 관리할 때 발생하는 호환성 문제 회피 + Docker 재시작 시 초기화 자동 적용)

### Day 1 (월) ✅
**목표: 프로젝트 생성 + Docker 환경**

- [x] Spring Boot 3.4.4 프로젝트 생성
  - groupId: `com.youthletter`
  - 의존성: Web, JPA, Security, Redis, Validation, Actuator, Lombok, PostgreSQL
- [x] `build.gradle` 추가 의존성 (JWT 0.12.6, QueryDSL 5.1.0, Jsoup 1.18.3, WebFlux, OAuth2, hypersistence-utils, pgvector)
- [x] `docker-compose.dev.yml` 작성 (pgvector/pgvector:pg16 + redis:7-alpine)
- [x] `docker compose up -d` 실행 → DB, Redis 기동 확인
- [x] `application.yml` 기본 구조 작성 (local/prod 프로필 분리)
- [x] `GET /actuator/health` 헬스체크 확인

---

### Day 2 (화) ✅
**목표: 패키지 구조 + 공통 유틸 완성**

- [x] 전체 패키지 디렉토리 구조 생성 (`config/`, `domain/`, `global/`)
- [x] `GlobalResponse<T>` 공통 응답 wrapper 구현 (`status`, `message`, `data`)
- [x] `ErrorCode` enum 작성 (40xx 인증, 41xx 사용자, 42xx 뉴스, 43xx 용어, 44xx 정책, 50xx 서버)
- [x] `BusinessException` 구현 (errorCode + detailMessage)
- [x] `GlobalExceptionHandler` 구현 (`BusinessException`, `MethodArgumentNotValidException`, `AccessDeniedException`, `Exception`)
- [x] `SecurityUtil.getCurrentUserId()` 구현 (SecurityContextHolder 기반 정적 유틸)
- [x] `BaseEntity` (createdAt), `BaseTimeEntity` (createdAt + updatedAt) 구현

---

### Day 3 (수) ✅
**목표: DB 스키마 + 엔티티**

> **Flyway 대신 init.sql 사용** — `docker/postgres/init.sql`에 전체 스키마 통합 관리

- [x] `docker/postgres/init.sql` 작성 (총 20개 테이블, 17개 인덱스, 6개 트리거, 9개 RLS 정책)
  - pgvector, ltree, pg_trgm extension 활성화
  - tsvector 자동 갱신 트리거
  - updated_at 자동 갱신 트리거
  - RLS (youthletter_app role 기반)
- [x] `User` 엔티티 작성 (role, interests JSONB 포함)
- [x] `UserProfile` 엔티티 작성
- [x] `OAuthAccount` 엔티티 작성

---

### Day 4 (목) ✅
**목표: JWT 인증 구현**

- [x] `JwtTokenProvider` 구현
  - `createAccessToken(userId, email, role)` — 15분, role 클레임 포함
  - `createRefreshToken(userId)` — 7일, Redis 저장
  - `getUserIdFromToken(token)`
  - `validateToken(token)`
  - `blacklistAccessToken(token)` — **AT 블랙리스트 (로그아웃 보안)**
  - `isBlacklisted(token)` — Redis 조회
- [x] `JwtAuthenticationFilter` 구현 (`OncePerRequestFilter` + 블랙리스트 체크)
- [x] `RedisConfig` 구현 (`RedisTemplate<String, Object>` bean)
- [x] `SecurityConfig` 기본 구조 작성 (STATELESS + CORS + permitAll 경로)

---

### Day 5 (금) ✅
**목표: Auth 모듈 완성 (이메일)**

- [x] `UserRepository` 구현 (`findByEmail`, `existsByEmail`, `existsByNickname`)
- [x] `AuthService` 구현
  - `signup(SignupRequest)` — BCrypt 암호화, role='USER' 설정, JWT 발급
  - `login(LoginRequest)` — 비밀번호 검증, OAuth 전용 계정 예외 처리
  - `reissue(refreshToken)` — Redis RT 검증 + 새 AT/RT 발급
  - `logout(userId, accessToken)` — **AT 블랙리스트 + RT 삭제**
  - `withdraw(userId, accessToken)` — **soft delete + AT 블랙리스트 + RT 삭제**
- [x] `AuthController` 구현 (5개 엔드포인트)
- [x] 로컬 테스트 콘솔 (`http://localhost:8080/test.html`) 생성

---

## WEEK 2 — Auth OAuth2 + User Profile ✅ 완료

> **완료일: 2026-04-08~09**

### Day 6 (월) ✅
**목표: 카카오 소셜 로그인**

- [x] `application.yml` 카카오 OAuth2 설정 (`client-id`, `client-secret`, `scope: profile_nickname, account_email`)
- [x] `CustomOAuth2UserService` 구현
  - `KakaoUserInfo`, `GoogleUserInfo` 파서 구현
  - provider + providerId 기준 기존 계정 조회/생성
  - 이메일 기준 기존 계정 연동 (다른 OAuth로 이미 가입한 경우)
  - `OAuthAccount` 생성 및 연결
- [x] `OAuth2AuthenticationSuccessHandler` 구현
  - 로그인 성공 → role 포함 JWT 발급 → 프론트 리다이렉트
- [x] `OAuth2AuthenticationFailureHandler` 구현
- [x] 카카오 개발자 콘솔 redirect URI: `http://localhost:8080/login/oauth2/code/kakao`
- [x] 테스트 콘솔에서 카카오 로그인 → JWT 발급 확인

---

### Day 7 (화) ✅
**목표: 구글 소셜 로그인 + SecurityConfig 완성**

- [x] 구글 OAuth2 설정 추가 (`scope: profile, email`)
- [x] `CustomOAuth2UserService`에 구글 응답 처리 추가
- [x] `SecurityConfig` 최종 완성
  - permitAll: `/api/auth/**`, `/api/news`, `/api/news/today`, `/api/news/{id}`, `/api/terms/**`, `/api/guides/**`, Swagger, health, 테스트 HTML
  - `/api/admin/**` → `hasRole("ADMIN")` **ROLE_ADMIN 전용**
  - 나머지 → `authenticated()`
- [x] Role 기반 접근 제어 구현 (`ROLE_USER` / `ROLE_ADMIN`)
- [x] `AccessDeniedException` → GlobalExceptionHandler에서 JSON 응답 처리
- [x] 시드 데이터에 ADMIN 유저 추가 (`admin@youthletter.com`)

---

### Day 8 (수) ✅
**목표: User Profile 모듈**

> **설계 변경:** `UserCategory` 별도 테이블 대신 `users.interests JSONB`로 통합 관리 (단순성 + 쿼리 성능)

- [x] `UserProfile` 엔티티 (employment_status, annual_income, is_married, household_members, education_level)
- [x] `UserProfileRepository` 구현
- [x] `UserService` 구현
  - `getMyProfile(userId)` → `UserProfileResponse` (interests JSON 파싱)
  - `updateProfile(userId, request)` — 닉네임 중복 체크 후 수정
  - `updateCategories(userId, request)` — interests JSONB 업데이트
  - `updatePolicyProfile(userId, request)` — birthDate/regionCode/occupation/incomeLevel
- [x] `UserController` 구현 (5개 엔드포인트)
  - `GET /api/users/me`
  - `PATCH /api/users/me`
  - `PUT /api/users/me/categories`
  - `PUT /api/users/me/policy-profile`
  - `GET /api/users/me/learning-stats`

---

### Day 9 (목) ✅
**목표: LearningStats + 북마크/학습용어 목록**

- [x] `UserService.getLearningStats(userId)` 구현
  - 읽은 뉴스 수: `reading_history` COUNT
  - 학습 용어 수: `term_lookup_log` DISTINCT term_id COUNT
  - 연속 학습일: `reading_history.read_at::DATE` DISTINCT → 역산 로직
- [x] `NewsBookmark` 엔티티 + `NewsBookmarkRepository` 구현
- [x] `NewsService.toggleBookmark()`, `getMyBookmarks()` 구현
- [x] `TermService.learnTerm()`, `getLearnedTerms()` 구현 (term_lookup_log 재활용)
- [x] `GET /api/users/me/bookmarks` (뉴스 북마크 목록, 페이지네이션)
- [x] `GET /api/users/me/learned-terms` (학습 용어 목록, 중복 제거)

---

### Day 10 (금) ✅
**목표: Week 1~2 리뷰 + 미완료 항목 처리**

> **Flyway V4~V7 생략** — init.sql로 전체 스키마 통합 관리 (위 Day 3 설명 참고)

- [x] JSONB 필드 전 엔티티 `@Type(JsonType.class)` 적용 (User, UserProfile, Policy, SpendingEvent, SpendingSnapshot, SpendingInsight, ChatMessage)
- [x] AT 블랙리스트 로그아웃 구현 (Redis `blacklist:{token}` TTL 관리)
- [x] `SecurityUtil` 정적 유틸 구현
- [x] 테스트 콘솔 (`test.html`) — Auth/User/News/Term/Admin 전체 섹션
- [x] `docs/DEV_SCHEDULE.md` 문서화 (오늘 할 일 + 진행 현황)
- [x] Week 1~2 구현 내용 전체 리뷰 완료

---

## WEEK 3 — News 모듈 + 크롤러 ✅ 완료

> **완료일: 2026-04-24**  
> **설계 변경:** `News` 단일 엔티티 대신 `NewsArticle` + `NewsSummary` 분리 (Temporal Versioning — AI 모델 업그레이드 시 이전 요약 보존).  
> `NewsRead` 대신 `activity.ReadingHistory` 사용 (Activity 도메인으로 분리).  
> AI 호출을 summary/ner/insight 분리 대신 `AiProcessResponse` 통합 응답으로 단순화.

### Day 11 (월) ✅
**목표: News 엔티티 + Repository**

- [x] `NewsArticle` 엔티티 작성 (`news_articles` 테이블 — 크롤링 원본)
- [x] `NewsSummary` 엔티티 작성 (`valid_from`/`valid_to` Temporal Versioning)
- [x] `ArticleTermHighlight` 엔티티 작성 (본문 내 용어 위치 저장)
- [x] `ReadingHistory` 엔티티 작성 (`activity` 도메인, `read_duration_seconds` 포함)
- [x] `NewsBookmark` 엔티티 작성
- [x] `NewsArticleRepository` 작성
  - `existsBySourceUrl` (중복 체크)
  - `findTodayArticles` — `crawledAt >= :startOfDay` JPQL
  - `findByCategory`, `findAllByOrderByPublishedAtDesc` 페이지네이션
- [x] `NewsSummaryRepository.findByArticleIdAndValidToIsNull` (현재 유효 버전 조회)
- [x] `ReadingHistoryRepository`, `NewsBookmarkRepository` 작성

---

### Day 12 (화) ✅
**목표: NewsService + NewsController**

- [x] `NewsService` 구현
  - `getNewsList(category, pageable)` — 카테고리 필터 + `NewsSummary` 조인
  - `getNewsDetail(articleId)` — content + summary + `ArticleTermHighlight` 목록
  - `markAsRead(userId, articleId, durationSeconds)` — `ActivityService.trackReading()` 위임
  - `toggleBookmark(userId, articleId)` — 있으면 삭제, 없으면 추가
  - `getTodayNews(userId)` — 관심 카테고리 우선 정렬 + AI 요약 포함
  - `getMyBookmarks(userId, pageable)` — 북마크 목록
- [x] `NewsController` 구현 (5개 엔드포인트)
- [x] 뉴스 없는 경우 빈 배열 반환 확인

---

### Day 13 (수) ✅
**목표: AiServiceClient stub 구현**

- [x] `WebClientConfig` 구현 (AI 서비스용 `WebClient` bean)
- [x] `AiProcessRequest` / `AiProcessResponse` DTO 작성 (summary + nerTerms + embedding 통합)
- [x] `AiServiceClient` 구현
  - `process(AiProcessRequest)` — stub 분기 (`useStub` 플래그)
  - stub 응답: 3줄 요약 문자열 + 빈 NER 목록 + 0벡터(1536차원)
  - 실제 호출: `POST /api/ai/process` (Node.js AI 서버)
  - `isStubMode()` / `setStubMode(boolean)` — 런타임 토글 지원
- [x] `application.yml` `ai-service.use-stub: true` 확인

---

### Day 14 (목) ✅
**목표: NewsProcessingService + 크롤러 기반 구조**

- [x] `CrawlerConfig` 작성 (`@EnableScheduling` 포함)
- [x] `NewsProcessingService` 구현
  - `processAll()` — 크롤링 → AI 처리 → `NewsArticle` + `NewsSummary` 저장
  - `scheduledProcess()` — `@Scheduled(cron = "0 0 */6 * * *")` 6시간마다 자동 실행
  - `CrawlStatus` record (lastRunAt, savedCount, failedCount, status)
  - `AtomicReference<CrawlStatus>` 인메모리 상태 관리 (NEVER_RUN / RUNNING / SUCCESS / PARTIAL)
- [x] `HankyungCrawlerService` 기본 구조 작성
  - `BASE_URL = https://www.hankyung.com`, `FINANCE_URL = /economy`
  - 목록 선택자: `ul.news-list a`, `div.article-list a` + fallback `a[href*='/article/']`

---

### Day 15 (금) ✅
**목표: 크롤러 완성 + Admin 엔드포인트**

- [x] `HankyungCrawlerService.crawl()` 완성
  - 목록 페이지 파싱 → `/article/` 포함 URL 추출 (중복 제거)
  - 기사 상세 페이지: `h1.headline`, `div#articletxt` 등 다중 selector fallback
  - `CrawledArticleDto` 반환
- [x] `NewsProcessingService` 내 `AtomicReference<CrawlStatus>` 상태 관리
- [x] `AdminCrawlController` 구현
  - `POST /api/admin/crawl/trigger` → 수동 크롤링 즉시 실행
  - `GET /api/admin/crawl/status` → 마지막 실행 상태 조회
  - `POST /api/admin/crawl/ai-stub` → stub 모드 런타임 토글
- [x] Git push (현재 `feature/test-ci` 브랜치)

---

## WEEK 4 — Term Dictionary + Policy

### Day 16 (월)
**목표: Term 모듈 완성**

- [ ] `Term`, `UserTermLearn` 엔티티 작성
- [ ] `TermRepository` 작성
  - `searchByKeyword` native query (tsvector `@@` 연산자)
- [ ] `TermLearnRepository` 작성 (upsert: `existsByUserIdAndTermId` → save)
- [ ] `TermService` 구현
  - `getTermList(category)`
  - `searchTerms(keyword)`
  - `getTermDetail(termId, userId)` — 관련 뉴스 목록 포함
  - `learnTerm(termId, userId)` — upsert
- [ ] `TermController` 구현 (4개 엔드포인트)
- [ ] 테스트 데이터로 용어 5~10개 INSERT (`V6` 마이그레이션에 seed data 추가)

---

### Day 17 (화)
**목표: `GET /api/users/me/learned-terms` 연결 + Term-News 연관**

- [ ] `UserService.getLearnedTerms(Long userId)` 완성 (실제 DB 연결)
- [ ] `TermDetailResponse`에 관련 뉴스 목록 포함
  - `ner_terms` jsonb에 해당 용어가 포함된 뉴스 조회 (`@> '[{"term":"ETF"}]'::jsonb`)
- [ ] tsvector 검색 동작 확인 테스트

---

### Day 18 (수)
**목표: Flyway V8 + Policy 엔티티/Repository**

- [ ] `V8__create_policies_table.sql` 작성 및 적용
- [ ] `Policy`, `PolicyBookmark` 엔티티 작성
- [ ] `PolicyRepository` 기본 JPA 작성
- [ ] `QueryDslConfig` 작성 (`JPAQueryFactory` bean)
- [ ] `PolicyQueryRepository` 작성 (QueryDSL 동적 쿼리 — CLAUDE.md Module 5 참고)

---

### Day 19 (목)
**목표: PolicyService + PolicyController**

- [ ] `PolicyService` 구현
  - `getPolicyList(category, region, pageable)`
  - `getPolicyDetail(policyId)`
  - `getRecommendedPolicies(Long userId)` — UserPolicyProfile 조회 → 동적 쿼리
  - `toggleBookmark(policyId, userId)`
- [ ] `PolicyController` 구현 (4개 엔드포인트)
- [ ] 테스트 정책 데이터 5개 INSERT
- [ ] 프로필 기반 추천 필터 동작 확인

---

### Day 20 (금)
**목표: PolicyScheduler + 알림 모듈 기반**

- [ ] `V11__create_notifications_table.sql` 작성 및 적용
- [ ] `Notification` 엔티티 작성
- [ ] `NotificationRepository` 작성
- [ ] `NotificationService.createPolicyDeadlineNotifications()` 구현
- [ ] `PolicyScheduler` 구현
  - `@Scheduled(cron = "0 0 9 * * *")`
  - D-7/D-3/D-1 정책 조회 → 북마크 유저 조회 → `Notification` 생성
- [ ] `NotificationController` 구현 (3개 엔드포인트)
- [ ] Git push (feature/term, feature/policy, feature/notification → develop PR)

---

## WEEK 5 — Spending 모듈

### Day 21 (월)
**목표: Flyway V9 + Spending 엔티티**

- [ ] `V9__create_spendings_table.sql` 작성 및 적용
  - `spending_category` ENUM 타입 포함
- [ ] `Spending`, `SpendingBudget` 엔티티 작성
  - `@Enumerated(EnumType.STRING)` 사용
- [ ] `SpendingRepository`, `BudgetRepository` 작성
- [ ] CRUD 기본 동작 확인

---

### Day 22 (화)
**목표: 소비 CRUD + 월별 목록**

- [ ] `SpendingService` 구현
  - `addSpending(Long userId, SpendingRequest)`
  - `updateSpending(Long userId, Long spendingId, SpendingRequest)` — 본인 것만 수정
  - `deleteSpending(Long userId, Long spendingId)` — soft delete
  - `getMonthlyList(Long userId, int year, int month)`
- [ ] `SpendingController` 구현 (CRUD 4개 엔드포인트)
- [ ] 소비 추가/수정/삭제/조회 Postman 테스트

---

### Day 23 (수)
**목표: 소비 집계 + 트렌드**

- [ ] `SpendingRepository.sumByCategory()` native query 구현
- [ ] `SpendingRepository.findMonthlyTrend()` native query (LAG 윈도우 함수)
- [ ] `SpendingService`
  - `getMonthlySummary(Long userId, int year, int month)` → `SpendingSummaryResponse`
  - `getMonthlyTrend(Long userId)` → `SpendingTrendResponse`
- [ ] `GET /api/spending/summary`, `GET /api/spending/trend` 엔드포인트 추가
- [ ] 테스트 데이터 20건 INSERT 후 집계 확인

---

### Day 24 (목)
**목표: 예산 설정 + AI 인사이트 stub**

- [ ] `SpendingService`
  - `setBudget(Long userId, int monthlyBudget)` — upsert
  - `getBudgetStatus(Long userId)` — (이번달 합계 / 예산) 퍼센트 계산
  - `getAiInsight(Long userId)` — `AiServiceClient.getInsight()` stub 호출
- [ ] `POST /api/spending/budget`, `GET /api/spending/budget/status`, `POST /api/spending/ai-insight` 엔드포인트 추가
- [ ] 예산 달성률 계산 로직 단위 테스트

---

### Day 25 (금)
**목표: Week 5 리뷰 + Spending 모듈 통합 테스트**

- [ ] 소비 등록 → 집계 → 트렌드 → 예산 달성률 전체 흐름 Postman 테스트
- [ ] 엣지 케이스 처리
  - 소비 내역 없을 때 집계: 빈 배열 반환 (NPE 방지)
  - 예산 미설정 상태에서 `getBudgetStatus()` 호출 시 graceful 처리
- [ ] Git push (feature/spending → develop PR)

---

## WEEK 6 — Stock + 마무리 모듈

### Day 26 (월)
**목표: Flyway V10 + Stock 모듈**

- [ ] `V10__create_stock_watchlists.sql` 작성 및 적용
- [ ] `StockWatchlist` 엔티티 작성
- [ ] `WatchlistRepository` 작성
- [ ] 외부 주가 API 선택 및 테스트
  - 옵션 A: 한국투자증권 Open API
  - 옵션 B: Yahoo Finance (`https://query1.finance.yahoo.com/v8/finance/chart/{ticker}`)
- [ ] `StockService`
  - `getStockPrice(String ticker)` — Redis 캐시 우선 조회 → miss 시 외부 API 호출 → 저장
  - `addWatchlist(Long userId, String ticker)`
  - `getWatchlist(Long userId)` — 종목별 현재가 포함
  - `removeWatchlist(Long userId, String ticker)`

---

### Day 27 (화)
**목표: StockController + Redis 캐시 완성**

- [ ] `StockController` 구현 (4개 엔드포인트)
- [ ] Redis TTL 캐시 확인 (`STOCK:{ticker}` 키, 5분 TTL)
- [ ] 캐시 hit/miss 로그 확인
- [ ] 관심 종목 CRUD Postman 테스트

---

### Day 28 (수)
**목표: `GET /api/users/me/bookmarks` 뉴스 + 정책 통합**

- [ ] 뉴스 북마크 목록 → `UserService.getNewsBookmarks()` 완성
- [ ] 정책 북마크 목록 조회 엔드포인트 추가 (`GET /api/users/me/policy-bookmarks`)
- [ ] 통합 테스트: 북마크 추가 → 목록 조회 → 삭제 흐름

---

### Day 29 (목)
**목표: 전체 API 통합 테스트 + Swagger**

- [ ] `springdoc-openapi` 의존성 추가
- [ ] 주요 API `@Operation`, `@ApiResponse` 어노테이션 추가
- [ ] `http://localhost:8080/swagger-ui.html` 전체 엔드포인트 확인
- [ ] 인증 필요 API → Swagger에서 Bearer 토큰 설정 후 테스트
- [ ] 미완료 엔드포인트 목록 정리

---

### Day 30 (금)
**목표: Week 6 리뷰 + develop 브랜치 머지**

- [ ] 모든 feature 브랜치 PR → develop 머지 완료
- [ ] develop 브랜치 기준 전체 빌드 확인 (`./gradlew build`)
- [ ] 발견된 버그 목록 이슈로 등록
- [ ] Git tag: `v0.1.0-dev` (MVP 기능 완성 기준점)

---

## WEEK 7 — 예외 처리 강화 + 보안

### Day 31 (월)
**목표: 입력값 검증 전체 추가**

- [ ] 모든 RequestDTO에 `@Valid` 어노테이션 + Constraint 추가
  ```java
  // 예시
  public record SignupRequest(
      @Email @NotBlank String email,
      @Size(min = 8, max = 20) @NotBlank String password,
      @Size(min = 2, max = 20) @NotBlank String nickname
  ) {}
  ```
- [ ] `GlobalExceptionHandler`에 `MethodArgumentNotValidException` 처리 추가
- [ ] 잘못된 입력값 시 400 + 필드별 에러 메시지 반환 확인

---

### Day 32 (화)
**목표: Rate Limiting + 보안 강화**

- [ ] Redis 기반 Rate Limit 구현 (크롤링 수동 실행, AI 인사이트 요청)
  ```
  RATE:{userId}:{endpoint}  → 호출 횟수 (TTL 1분)
  ```
- [ ] Access Token 블랙리스트 처리 (로그아웃 후 재사용 방지) 확인
- [ ] CORS 설정 (`SecurityConfig`)
  - 허용 origin: `http://localhost:3001` (프론트 개발 서버)

---

### Day 33 (수)
**목표: 로깅 + 모니터링**

- [ ] `logback-spring.xml` 설정 (콘솔 + 파일 로그)
- [ ] 요청/응답 로깅 인터셉터 구현 (URI, 메서드, 소요 시간)
- [ ] 크롤러 실행 결과 로그 강화
- [ ] Spring Actuator health endpoint 확장
  - DB, Redis 연결 상태 포함

---

### Day 34 (목)
**목표: 정책 공공 API 연동**

- [ ] 청년정책 공공 API 키 발급 (공공데이터포털 또는 온통청년 API)
- [ ] `PolicyScheduler.collectPolicies()` 구현
  - `@Scheduled(cron = "0 0 3 * * *")` (매일 새벽 3시)
  - 외부 API 응답 → `Policy` upsert (`external_id` 기준)
- [ ] 실제 정책 데이터 수집 테스트

---

### Day 35 (금)
**목표: Week 7 리뷰 + 성능 최적화 1차**

- [ ] N+1 쿼리 점검 (뉴스 목록, 정책 목록)
  - `@EntityGraph` 또는 fetch join 적용
- [ ] 뉴스 목록 Redis 캐시 추가 (카테고리별 캐시, TTL 10분)
- [ ] `EXPLAIN ANALYZE` 로 쿼리 실행 계획 확인
- [ ] Git push

---

## WEEK 8 — CI/CD + Docker 배포

### Day 36 (월)
**목표: Dockerfile 작성**

- [ ] 멀티스테이지 `Dockerfile` 작성
  ```dockerfile
  # Stage 1: Build
  FROM gradle:8.5-jdk17 AS builder
  WORKDIR /app
  COPY . .
  RUN gradle build -x test

  # Stage 2: Run
  FROM eclipse-temurin:17-jre-alpine
  WORKDIR /app
  COPY --from=builder /app/build/libs/*.jar app.jar
  EXPOSE 8080
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```
- [ ] `docker build` + `docker run` 로컬 확인
- [ ] `docker-compose.yml` 프로덕션용 추가 (`docker-compose.prod.yml`)
  - Spring Boot + PostgreSQL + Redis 전체 구성

---

### Day 37 (화)
**목표: GitHub Actions CI**

- [ ] `.github/workflows/ci.yml` 작성
  ```yaml
  on:
    pull_request:
      branches: [develop, main]
  jobs:
    build:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with: { java-version: '17', distribution: 'temurin' }
        - run: ./gradlew build -x test
        - run: ./gradlew test
  ```
- [ ] develop PR 생성 → CI 통과 확인

---

### Day 38 (수)
**목표: GitHub Actions CD**

- [ ] `.github/workflows/cd.yml` 작성
  ```yaml
  on:
    push:
      branches: [main]
  jobs:
    deploy:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - name: Build Docker image
          run: docker build -t youthletter-api .
        - name: Deploy via SSH
          # SSH로 서버 접속 → docker compose up
  ```
- [ ] GitHub Secrets 등록 (DB 비밀번호, JWT Secret, API Key 등)
- [ ] main 브랜치 머지 → 자동 배포 확인

---

### Day 39 (목)
**목표: 배포 서버 환경 세팅**

- [ ] 서버 Docker, Docker Compose 설치
- [ ] `.env` 파일 구성 (환경 변수 전체)
- [ ] `docker compose up -d` 전체 스택 기동
- [ ] 배포된 API Health Check 확인
- [ ] Flyway 마이그레이션 자동 실행 확인

---

### Day 40 (금)
**목표: Week 8 리뷰 + 배포 안정화**

- [ ] 배포 환경에서 주요 API Postman 테스트
- [ ] 크롤러 스케줄러 정상 동작 확인 (로그)
- [ ] 에러 발생 시 알림 채널 설정 (Slack 또는 Discord webhook)
- [ ] `v0.2.0` 태그

---

## WEEK 9 — AI 연동 + 통합 테스트

### Day 41 (월)
**목표: AI 서비스 API 계약 확인 + 연동 준비**

- [ ] 혜린이와 내부 API 계약 문서 최종 확인
  - `POST /summarize` 요청/응답 스펙
  - `POST /ner` 요청/응답 스펙
  - `POST /insight` 요청/응답 스펙
- [ ] `AiServiceClient` stub → 실제 WebClient 호출 코드 구현
- [ ] AI 서비스 로컬 기동 확인 (포트 3000)

---

### Day 42 (화)
**목표: AI 요약/NER 연동**

- [ ] `AiServiceClient.getSummary()` stub 제거 → 실제 호출
- [ ] `AiServiceClient.getNer()` stub 제거 → 실제 호출
- [ ] `application.yml` `ai.service.stub-mode: false`
- [ ] 크롤링 1회 실행 → `ai_summary`, `ner_terms` DB 저장 확인
- [ ] `GET /api/news/{newsId}` 응답에 실제 요약 포함 확인

---

### Day 43 (수)
**목표: AI 소비 인사이트 연동 + SSE 확인**

- [ ] `AiServiceClient.getInsight()` stub 제거 → 실제 호출
- [ ] SSE 스트리밍 응답 처리 (혜린이 서비스에서 SSE로 응답하는 경우)
  - Spring Boot `SseEmitter` 또는 WebClient streaming 처리
- [ ] `POST /api/spending/ai-insight` 실제 AI 응답 확인

---

### Day 44 (목)
**목표: 벡터 임베딩 연동**

- [ ] 크롤링 후 임베딩 생성 → `news.embedding` 저장 확인
- [ ] `NewsRepository.findSimilarNews()` native query 동작 확인
- [ ] `GET /api/news/{newsId}` 의 "관련 뉴스 추천" 기능 연결

---

### Day 45 (금)
**목표: 프론트 연동 테스트**

- [ ] 소윤이(프론트)와 API 연동 테스트
  - 인증 흐름 (카카오 로그인 → JWT → API 호출)
  - 뉴스 목록 → 상세 → 읽음 처리
  - 정책 추천 화면
- [ ] CORS 오류 대응
- [ ] 발견된 API 스펙 불일치 수정

---

## WEEK 10 — 최종 통합 + 발표 준비

### Day 46 (월)
**목표: 성능 최적화 2차**

- [ ] DB 인덱스 최적화 (`EXPLAIN ANALYZE` 기반)
- [ ] 자주 호출되는 API Redis 캐시 확인
- [ ] 불필요한 N+1 쿼리 전수 점검
- [ ] 응답 속도 측정 (목표: 주요 API 200ms 이내)

---

### Day 47 (화)
**목표: 시나리오 기반 E2E 테스트**

- [ ] 신규 유저 시나리오
  1. 회원가입 → 관심 카테고리 설정 → 정책 프로필 설정
  2. 오늘의 뉴스 조회 → 상세 읽기 → 용어 학습 → 북마크
  3. 소비 입력 → 집계 조회 → AI 인사이트
  4. 정책 추천 → 정책 북마크
- [ ] 발견 버그 즉시 수정

---

### Day 48 (수)
**목표: 격주 보고서 4회분 내용 정리**

- [ ] 구현 완료 기능 목록 문서화
- [ ] API 문서 (Swagger) 최종 정리
- [ ] 아키텍처 다이어그램 갱신
- [ ] 특이 사항 및 기술적 결정 사항 기록

---

### Day 49 (목)
**목표: 버그 수정 + 안정화**

- [ ] 미해결 이슈 전체 처리
- [ ] 배포 환경 최종 확인
- [ ] main 브랜치 `v1.0.0` 태그

---

### Day 50 (금)
**목표: 전시회 준비**

- [ ] 데모용 시드 데이터 투입 (뉴스 50건, 정책 20건, 용어 30건)
- [ ] 데모 시나리오 스크립트 작성
- [ ] 배포 서버 안정성 최종 점검
- [ ] README.md 최종 작성 (실행 방법, API 문서 링크)

---

## 진행 현황 트래커

| 주차 | 목표 | 상태 |
|------|------|------|
| Week 1 | 프로젝트 세팅 + JWT 인증 | ✅ |
| Week 2 | OAuth2 + User Profile | ✅ |
| Week 3 | News 모듈 + 크롤러 | ✅ |
| Week 4 | Term + Policy | ⬜ |
| Week 5 | Spending | ⬜ |
| Week 6 | Stock + 통합 | ⬜ |
| Week 7 | 예외 처리 + 보안 + 공공 API | ⬜ |
| Week 8 | CI/CD + 배포 | ⬜ |
| Week 9 | AI 연동 + 프론트 연동 | ⬜ |
| Week 10 | 최종 안정화 + 전시 준비 | ⬜ |

---

## 브랜치 전략

```
main
 └── develop
      ├── feature/auth
      ├── feature/user
      ├── feature/news
      ├── feature/crawler
      ├── feature/term
      ├── feature/policy
      ├── feature/spending
      ├── feature/stock
      ├── feature/notification
      └── feature/admin
```

- feature/* → develop: PR + CI 통과 후 머지
- develop → main: 주요 마일스톤마다 (v0.1.0, v0.2.0, v1.0.0)
- main 푸시 → CD 자동 배포
