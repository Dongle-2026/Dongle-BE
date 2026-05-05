# DEV_SCHEDULE.md — 백엔드 일별 개발 계획

> 총 기간: 10주 (50일)  
> 기준: 평일 기준, 하루 4~6시간 개발  
> 완료 시 체크박스 체크: `[ ]` → `[x]`  
> 최종 수정: 2026-05-05 (WEEK 5 완료)

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

## WEEK 4 — Term Dictionary + Policy ✅ 완료

> **완료일: 2026-05-04**  
> **브랜치: `feature/week4-term-policy`**  
> **설계 변경:** `Term`/`UserTermLearn` 대신 `FinancialTerm` + `TermLookupLog` 사용 (Activity 도메인 통합). ltree 기반 계층 카테고리, pg_trgm 자동완성, pgvector 유사도 검색 추가. `Notification` 엔티티는 `policy_notifications`로 Policy 도메인에 귀속.

### Day 16 (월) ✅
**목표: Term 모듈 완성**

- [x] `FinancialTerm` 엔티티 작성 (ltree `path`, tsvector `search_vector`, VECTOR `embedding`)
- [x] `TermRelation` 엔티티 작성 (PARENT / SEE_ALSO 관계)
- [x] `ArticleTermHighlight` 엔티티 작성 (본문 내 용어 위치, positionStart/End)
- [x] `FinancialTermRepository` 작성
  - `findByPathDescendants` — ltree `<@` 연산자 (CAST 필수)
  - `searchByText` — tsvector `@@` + `ts_rank`
  - `findBySimilarName` — pg_trgm `%` 유사도
  - `findByEmbeddingSimilarity` — pgvector `<=>` 코사인 유사도
- [x] `TermService` 구현 (5개 메서드)
- [x] `TermController` 구현 (7개 엔드포인트)
- [x] `init.sql` 시드 데이터: 용어 10건, term_relations 4건

---

### Day 17 (화) ✅
**목표: 학습 이력 + Term-News 연관**

- [x] `TermLookupLogRepository` 작성 (`findByUserIdWithTermOrderByDesc`, `existsByUserIdAndTermId`)
- [x] `ArticleTermHighlightRepository.findTop5ArticlesByTermId` (관련 뉴스 5건 native query)
- [x] `TermDetailResponse` — 관련 뉴스 목록 + `isLearned` 포함
- [x] `TermService.getLearnedTerms()` — LinkedHashMap으로 termId 기준 중복 제거
- [x] `GET /api/users/me/learned-terms` 완성

---

### Day 18 (수) ✅
**목표: Policy 엔티티/Repository**

- [x] `Policy` 엔티티 작성 (regionCode null=전국, ageMin/Max, incomeCriteria, occupationCriteria JSONB)
- [x] `PolicyBookmark` 엔티티 + `PolicyNotification` 엔티티 작성
- [x] `PolicyRepository` 작성 (`findMatchingPolicies`, `findPoliciesExpiringBetween`, pgvector 검색)
- [x] `PolicyBookmarkRepository` 작성
- [x] `QueryDslConfig` 작성 (`JPAQueryFactory` bean)
- [x] `PolicyQueryRepository` 작성 (QueryDSL 동적 쿼리)
  - `findActivePolicies(regionCode, pageable)` — regionCode null이면 전국 정책 포함
  - `findByProfile(age, regionCode, incomeCriteria, pageable)` — 4조건 동적 필터

---

### Day 19 (목) ✅
**목표: PolicyService + PolicyController**

- [x] `PolicyService` 구현
  - `getPolicyList(regionCode, pageable)`
  - `getPolicyDetail(policyId, userId)` — isBookmarked 포함
  - `getRecommendedPolicies(userId, pageable)` — birthDate null이면 기본 25세
  - `toggleBookmark(userId, policyId)` — 있으면 삭제(false), 없으면 추가(true)
- [x] `PolicyController` 구현 (4개 엔드포인트)
- [x] `init.sql` 시드 데이터: 정책 3건 (청년월세지원, 청년내일저축계좌, 부산청년취업지원금)

---

### Day 20 (금) ✅
**목표: PolicyScheduler + 단위 테스트**

- [x] `PolicyNotificationRepository` 작성 (미발송 알림 PostgreSQL native query — DATE 연산)
- [x] `PolicyScheduler` 구현 (`@Scheduled 09:00`, D-7/D-3/D-1 중복 방지 + `markAsSent()`)
- [x] `TermServiceTest` 12건 (Mockito 단위 테스트)
- [x] `PolicyServiceTest` 11건 (Mockito 단위 테스트)
- [x] Git push (`feature/week4-term-policy`)

---

## WEEK 5 — Spending 완성 + Notification + Stock ✅ 완료

> **완료일: 2026-05-05**  
> **브랜치: `feature/week5-spending-noti-stock`**  
> **설계 변경:** Event Sourcing 패턴 (`spending_events` append-only) + 스냅샷 집계. SpendingController `@AuthenticationPrincipal` 전환 완료. 알림은 기존 `policy_notifications` 테이블 재활용 (별도 notifications 테이블 불필요). Stock 시세는 Redis TTL 5분 stub으로 구현.

### Day 21 (월) ✅
**목표: Spending 엔티티 + Event Sourcing 기반 구조**

- [x] `SpendingEvent` 엔티티 작성 (`spending_events` — eventType: CREATED/UPDATED/DELETED)
- [x] `SpendingSnapshot` 엔티티 작성 (`spending_snapshots` — 월별 카테고리 집계 캐시)
- [x] `SpendingInsight` 엔티티 작성 (`spending_insights` — AI 분석 결과)
- [x] `SpendingEventRepository` 작성
  - `findBySpendingIdOrderByCreatedAtAsc` — 이벤트 재생용
  - `aggregateByCategory` — DISTINCT ON + CTE native query (최신 이벤트 기준 집계)
- [x] `SpendingSnapshotRepository` 작성

---

### Day 22 (화) ✅
**목표: 소비 CRUD**

- [x] `SpendingService` 구현
  - `createSpending(userId, request)` — CREATED 이벤트 삽입
  - `updateSpending(userId, spendingId, request)` — UPDATED 이벤트 삽입 + 소유권 검증
  - `deleteSpending(userId, spendingId)` — DELETED 이벤트 삽입 + 소유권 검증
  - `getDashboard(userId, month)` — 스냅샷 우선, 없으면 이벤트 집계
- [x] `SpendingController` 기본 4개 엔드포인트 구현

---

### Day 23 (수) ✅
**목표: 월별 목록 + 트렌드 + SecurityContext 수정**

- [x] `SpendingController` `@RequestParam Long userId` → `@AuthenticationPrincipal CustomUserPrincipal` 전체 수정
- [x] `SpendingEventRepository.findCurrentStateByUserAndPeriod` native query 추가 (DISTINCT ON)
- [x] `GET /api/spending?year=&month=` 월별 목록 엔드포인트 추가 → `SpendingListResponse`
- [x] `SpendingEventRepository.findMonthlyTrend` LAG 윈도우 함수 native query 추가
- [x] `GET /api/spending/trend` 6개월 트렌드 엔드포인트 추가 → `SpendingTrendResponse`
- [x] `SpendingBudget` 엔티티 + `SpendingBudgetRepository` 작성
- [x] `POST /api/spending/budget` 예산 upsert 엔드포인트 추가
- [x] `GET /api/spending/budget/status` 예산 달성률 엔드포인트 추가
- [x] `POST /api/spending/ai-insight` AI 인사이트 stub 엔드포인트 추가
- [x] `SpendingDashboardResponse.fromSnapshot` JSON→Map 파싱 완성 (Jackson ObjectMapper)
- [x] `init.sql`에 `spending_budgets` 테이블 추가

---

### Day 24 (목) ✅
**목표: Notification 모듈 + Stock 관심종목**

- [x] `notification` 도메인 신규 생성 (`policy_notifications` 테이블 재활용)
  - `PolicyNotificationRepository`에 `findByUserIdOrderBySentAndCreatedAt`, `findUnsentByUserId` 쿼리 추가
  - `NotificationResponse` DTO 작성
  - `NotificationService` 구현 (`getMyNotifications`, `markAsRead`, `markAllAsRead`)
  - `NotificationController` 구현 (3개 엔드포인트)
- [x] `GET /api/notifications` — 내 알림 목록 (미발송 우선)
- [x] `PATCH /api/notifications/{id}/read` — 단건 읽음 처리
- [x] `PATCH /api/notifications/read-all` — 전체 읽음 처리
- [x] `stock` 도메인 신규 생성
  - `StockWatchlist` 엔티티 + `WatchlistRepository` 작성
  - `StockPriceResponse`, `StockWatchlistResponse`, `WatchlistAddRequest` DTO 작성
  - `StockService` 구현 (Redis 5분 캐시 + stub 가격 생성, AAPL/TSLA/005930 등 8종목)
  - `StockController` 구현 (4개 엔드포인트)
- [x] `init.sql`에 `stock_watchlists` 테이블 + 인덱스 + RLS 추가
- [x] `GET /api/stocks/{ticker}` — 주가 시세 (stub, Redis TTL 5분)
- [x] `POST /api/stocks/watchlist` — 관심종목 추가
- [x] `GET /api/stocks/watchlist` — 관심종목 목록
- [x] `DELETE /api/stocks/watchlist/{ticker}` — 관심종목 삭제
- [x] `GET /api/users/me/policy-bookmarks` — 정책 북마크 목록 (UserController + PolicyService 추가)
- [x] `ErrorCode`에 소비(46xx)/주식(47xx) 에러 코드 추가
- [x] `SecurityConfig` `/api/stocks/{ticker}` 인증 불필요 경로 추가

---

### Day 25 (금) ✅
**목표: WEEK 5 통합 테스트 + Git push**

- [x] `SpendingServiceTest` 10건 (Mockito 단위 테스트)
  - CRUD 이벤트 저장 검증, 소유권 예외 처리, 집계, 트렌드 증감 계산, 예산 upsert, AI stub
- [x] Git push (`feature/week5-spending-noti-stock`)

---

## 기술 메모 (WEEK 5)

- **Event Sourcing 조회**: `DISTINCT ON (spending_id)` + `ORDER BY spending_id, created_at DESC`로 각 spending_id별 최신 이벤트만 추출. DELETED 이벤트는 필터링해 현재 유효한 소비만 반환.
- **LAG 윈도우 함수**: 6개월 트렌드 쿼리에서 서브쿼리로 월별 합계를 먼저 구하고, 외부 쿼리에서 LAG로 전월 비교. `SpendingTrendResponse.from()`에서 Java 레벨 증감 계산.
- **SpendingBudget**: DB UNIQUE 제약 (`user_id`)으로 중복 방지. 서비스에서 `findByUserId` 후 없으면 INSERT, 있으면 `update()` 호출 (dirty checking).
- **Notification 재활용**: `policy_notifications.is_sent = false`를 '읽지 않음' 상태로 사용. `markAsSent()`로 읽음 처리. `markAllAsRead`는 미발송 목록 전체 조회 후 일괄 처리.
- **StockService stub**: 종목별 기준가에 ±3% 랜덤 jitter 적용. 한국 종목(6자리 숫자)은 소수점 없이 원단위 반올림. Redis에 `StockPriceResponse` 객체 직렬화 저장 (TTL 5분).
- **StockPriceResponse Redis 직렬화**: `GenericJackson2JsonRedisSerializer` 사용 시 역직렬화를 위해 타입 정보 포함. `instanceof StockPriceResponse` 체크로 안전하게 캐스팅.

---

---

## WEEK 6 — Stock + FE 연동 + 통합 마무리

### Day 26 (월)
**목표: Stock 모듈 완성**

- [ ] `StockWatchlist` 엔티티 + `WatchlistRepository` 작성 (이미 `init.sql`에 테이블 있음)
- [ ] `StockService` 구현
  - `getStockPrice(String ticker)` — Yahoo Finance stub (Redis TTL 5분)
  - `addWatchlist(userId, ticker)`, `getWatchlist(userId)`, `removeWatchlist(userId, ticker)`
- [ ] `StockController` 구현 (4개 엔드포인트)

---

### Day 27 (화)
**목표: 정책 북마크 목록 + 프로필 API 완성**

- [ ] `GET /api/users/me/policy-bookmarks` 엔드포인트 추가
- [ ] `SpendingDashboardResponse.fromSnapshot` — categoryTotals JSON → Map 파싱 완성 (현재 TODO)
- [ ] 통합 테스트: 북마크 추가 → 목록 → 삭제 전 흐름

---

### Day 28 (수)
**목표: FE 연동 확대**

> **완료된 FE 연동 (2026-05-04):** 뉴스 목록/상세 (`GET /api/news`, `GET /api/news/{id}`)

- [ ] FE에 정책 목록 연동 (`GET /api/policies`)
- [ ] FE에 용어 검색 연동 (`GET /api/terms/search`)
- [ ] FE 소셜 로그인 플로우 확인 (카카오 OAuth2 콜백 → JWT → 쿠키)
- [ ] `.env.local` `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` 동작 확인

---

### Day 29 (목)
**목표: Swagger 최종 정리 + openapi.json 갱신**

- [ ] 전체 엔드포인트 `@Operation`, `@Tag` 어노테이션 일관성 점검
- [ ] `GET /v3/api-docs` 응답 → `docs/openapi.json` 갱신
- [ ] Swagger UI Bearer 토큰 인증 동작 확인
- [ ] 미완료/미문서화 엔드포인트 목록 정리

---

### Day 30 (금)
**목표: WEEK 6 리뷰 + develop 브랜치 머지**

- [ ] 모든 feature 브랜치 PR → develop 머지
- [ ] `./gradlew build` 전체 빌드 확인
- [ ] 발견 버그 이슈 등록
- [ ] Git tag: `v0.1.0-dev`

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
| Week 4 | Term + Policy | ✅ |
| Week 5 | Spending + Notification + Stock | ✅ |
| Week 6 | Stock + FE 연동 + 통합 | ⬜ |
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
