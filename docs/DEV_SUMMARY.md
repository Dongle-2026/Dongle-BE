# 유스레터 백엔드 개발 현황 문서

> 작성일: 2026-04-24  
> 담당: 정다은

---

## 1. 개발 현황 요약

| 영역 | 상태 | 비고 |
|------|------|------|
| 프로젝트 초기 세팅 | 완료 | Spring Boot 3.4.4 / Java 17 / Docker |
| OAuth2 로그인 (카카오/구글) | 완료 | JWT 발급까지 연동 |
| 이메일 회원가입/로그인 | 완료 | BCrypt + JWT |
| JWT 인증 필터 | 완료 | Access 15분 / Refresh 7일 / Redis 블랙리스트 |
| 사용자 프로필 API | 완료 | 조회/수정/관심카테고리/정책프로필/학습통계 |
| 뉴스 크롤링 파이프라인 | 완료 | Jsoup (한경), 스케줄러 + 수동 트리거 |
| 뉴스 API | 완료 | 목록/오늘/상세/읽음/북마크 토글 |
| 금융 용어 API | 완료 | ltree 계층/tsvector 전문검색/pg_trgm 자동완성 |
| 청년 정책 API | 완료 | 맞춤 추천/상세/북마크 토글 |
| 소비 관리 API | 완료 | Event Sourcing 구조, 월별 대시보드 |
| 금융 가이드 API | 완료 | 정적 가이드 콘텐츠 제공 |
| Swagger UI | 완료 | `/swagger-ui.html` |
| 헬스 체크 | 완료 | `/actuator/health` (DB + Redis 연결 확인) |
| AI 서비스 연동 | Stub 모드 | Node.js AI 서버 완성 후 전환 |
| AI 챗봇 (Agent) | 엔티티만 준비 | 아직 Controller/Service 없음 |
| 프론트 HTML (static) | 완료 | login·home·news-detail·mypage·test |

---

## 2. 파일 구조

```
youthletter-backend/
├── build.gradle
├── CLAUDE.md                          ← Claude Code 개발 가이드
├── DEV_SUMMARY.md                     ← 이 문서
│
└── src/main/
    ├── java/com/youthletter/
    │   │
    │   ├── YouthLetterApplication.java
    │   │
    │   ├── config/                    ← 전역 설정
    │   │   ├── SecurityConfig.java    ← Spring Security + OAuth2 + JWT 필터 연결
    │   │   ├── RedisConfig.java       ← Redis 직렬화 설정
    │   │   ├── WebClientConfig.java   ← AI 서비스 호출용 WebClient
    │   │   ├── SwaggerConfig.java     ← Swagger UI 설정
    │   │   ├── JpaAuditConfig.java    ← createdAt/updatedAt 자동 주입
    │   │   └── CrawlerConfig.java     ← @EnableScheduling 활성화
    │   │
    │   ├── domain/
    │   │   │
    │   │   ├── user/                  ← 사용자 인증 & 프로필
    │   │   │   ├── entity/
    │   │   │   │   ├── User.java              ← users 테이블
    │   │   │   │   ├── UserProfile.java       ← user_profiles 테이블 (취업상태/소득/결혼 등)
    │   │   │   │   └── OAuthAccount.java      ← oauth_accounts 테이블 (provider, provider_id)
    │   │   │   ├── repository/
    │   │   │   │   ├── UserRepository.java
    │   │   │   │   ├── UserProfileRepository.java
    │   │   │   │   └── OAuthAccountRepository.java
    │   │   │   ├── service/
    │   │   │   │   ├── AuthService.java       ← 회원가입/로그인/토큰 재발급/로그아웃/탈퇴
    │   │   │   │   └── UserService.java       ← 프로필 조회/수정, 학습통계
    │   │   │   ├── controller/
    │   │   │   │   ├── AuthController.java    ← POST /api/auth/**
    │   │   │   │   └── UserController.java    ← GET/PATCH/PUT /api/users/me/**
    │   │   │   └── dto/
    │   │   │       ├── SignupRequest.java
    │   │   │       ├── LoginRequest.java
    │   │   │       ├── TokenResponse.java
    │   │   │       ├── TokenReissueRequest.java
    │   │   │       ├── UserProfileResponse.java
    │   │   │       ├── ProfileUpdateRequest.java    ← { nickname }
    │   │   │       ├── CategoryUpdateRequest.java   ← { categories: [] }
    │   │   │       ├── PolicyProfileRequest.java    ← { birthDate, regionCode, occupation, incomeLevel }
    │   │   │       └── LearningStatsResponse.java
    │   │   │
    │   │   ├── news/                  ← 뉴스
    │   │   │   ├── entity/
    │   │   │   │   ├── NewsArticle.java            ← news_articles 테이블 (크롤링 원본)
    │   │   │   │   ├── NewsSummary.java            ← news_summaries 테이블 (Temporal Versioning)
    │   │   │   │   ├── NewsBookmark.java           ← news_bookmarks 테이블
    │   │   │   │   ├── ArticleTermHighlight.java   ← article_term_highlights 테이블
    │   │   │   │   └── News.java                  ← (레거시, 미사용)
    │   │   │   ├── repository/
    │   │   │   │   ├── NewsArticleRepository.java  ← findTodayArticles, existsBySourceUrl
    │   │   │   │   ├── NewsSummaryRepository.java  ← findByArticleIdAndValidToIsNull
    │   │   │   │   ├── NewsBookmarkRepository.java
    │   │   │   │   └── NewsRepository.java         ← (레거시, 미사용)
    │   │   │   ├── service/
    │   │   │   │   ├── NewsService.java            ← 뉴스 조회/북마크/읽음 처리
    │   │   │   │   └── NewsProcessingService.java  ← 크롤링→AI→DB 저장 파이프라인
    │   │   │   ├── controller/
    │   │   │   │   ├── NewsController.java         ← GET/POST /api/news/**
    │   │   │   │   └── AdminCrawlController.java   ← POST /api/admin/crawl/**
    │   │   │   ├── client/
    │   │   │   │   ├── AiServiceClient.java        ← Node.js AI 서버 호출 (현재 stub)
    │   │   │   │   └── dto/
    │   │   │   │       ├── AiProcessRequest.java
    │   │   │   │       └── AiProcessResponse.java
    │   │   │   └── dto/
    │   │   │       ├── NewsListResponse.java        ← { id, title, sourceName, category, summary, publishedAt }
    │   │   │       ├── NewsDetailResponse.java      ← + content, termHighlights
    │   │   │       └── NewsBookmarkResponse.java    ← { articleId, title, category, publishedAt, bookmarkedAt }
    │   │   │
    │   │   ├── crawler/               ← 뉴스 크롤러
    │   │   │   ├── service/
    │   │   │   │   └── HankyungCrawlerService.java ← Jsoup으로 한경 금융뉴스 파싱
    │   │   │   └── dto/
    │   │   │       └── CrawledArticleDto.java       ← { url, title, content, category, publishedAt }
    │   │   │
    │   │   ├── activity/              ← 사용자 활동 기록
    │   │   │   ├── entity/
    │   │   │   │   ├── ReadingHistory.java          ← reading_history 테이블
    │   │   │   │   └── TermLookupLog.java           ← term_lookup_logs 테이블
    │   │   │   ├── repository/
    │   │   │   │   ├── ReadingHistoryRepository.java
    │   │   │   │   └── TermLookupLogRepository.java
    │   │   │   └── service/
    │   │   │       └── ActivityService.java          ← 읽기/용어조회 이력 저장
    │   │   │
    │   │   ├── term/                  ← 금융 용어 사전
    │   │   │   ├── entity/
    │   │   │   │   ├── FinancialTerm.java           ← financial_terms 테이블 (ltree path)
    │   │   │   │   └── TermRelation.java            ← 용어 간 관계
    │   │   │   ├── repository/
    │   │   │   │   └── FinancialTermRepository.java ← ltree/tsvector/pgvector/pg_trgm 네이티브 쿼리
    │   │   │   ├── service/
    │   │   │   │   └── TermService.java             ← 용어 조회, 학습 처리, 학습 목록
    │   │   │   ├── controller/
    │   │   │   │   └── TermController.java          ← GET /api/terms/**
    │   │   │   └── dto/
    │   │   │       ├── TermListResponse.java
    │   │   │       └── LearnedTermResponse.java
    │   │   │
    │   │   ├── policy/                ← 청년 정책
    │   │   │   ├── entity/
    │   │   │   │   ├── Policy.java                 ← policies 테이블
    │   │   │   │   ├── PolicyBookmark.java
    │   │   │   │   └── PolicyNotification.java
    │   │   │   ├── repository/
    │   │   │   │   ├── PolicyRepository.java
    │   │   │   │   └── PolicyBookmarkRepository.java
    │   │   │   ├── service/
    │   │   │   │   └── PolicyService.java           ← 맞춤 추천, 북마크 토글
    │   │   │   ├── controller/
    │   │   │   │   └── PolicyController.java        ← GET/POST /api/policies/**
    │   │   │   └── dto/
    │   │   │       ├── PolicyListResponse.java
    │   │   │       └── PolicyDetailResponse.java
    │   │   │
    │   │   ├── spending/              ← 소비 관리 (Event Sourcing)
    │   │   │   ├── entity/
    │   │   │   │   ├── SpendingEvent.java           ← spending_events 테이블 (INSERT ONLY)
    │   │   │   │   ├── SpendingSnapshot.java        ← spending_snapshots 테이블 (월별 집계 캐시)
    │   │   │   │   └── SpendingInsight.java         ← spending_insights 테이블
    │   │   │   ├── repository/
    │   │   │   │   ├── SpendingEventRepository.java
    │   │   │   │   └── SpendingSnapshotRepository.java
    │   │   │   ├── service/
    │   │   │   │   └── SpendingService.java         ← 이벤트 삽입, 스냅샷 기반 대시보드
    │   │   │   ├── controller/
    │   │   │   │   └── SpendingController.java      ← GET/POST/PUT/DELETE /api/spending/**
    │   │   │   └── dto/
    │   │   │       ├── SpendingCreateRequest.java
    │   │   │       └── SpendingDashboardResponse.java
    │   │   │
    │   │   ├── guide/                 ← 금융 교육 가이드
    │   │   │   ├── entity/Guide.java               ← guides 테이블 (guideType, topic, difficultyLevel)
    │   │   │   ├── repository/GuideRepository.java
    │   │   │   └── controller/GuideController.java  ← GET /api/guides/**
    │   │   │
    │   │   └── agent/                 ← AI 챗봇 (엔티티만 준비, 미구현)
    │   │       └── entity/
    │   │           ├── ChatSession.java             ← chat_sessions 테이블
    │   │           ├── ChatMessage.java             ← chat_messages 테이블
    │   │           └── AgentFeedback.java           ← agent_feedbacks 테이블
    │   │
    │   └── global/
    │       ├── auth/
    │       │   ├── jwt/
    │       │   │   ├── JwtTokenProvider.java          ← 토큰 생성/검증/블랙리스트
    │       │   │   ├── JwtAuthenticationFilter.java   ← 매 요청 JWT 검증 필터
    │       │   │   └── CustomUserPrincipal.java        ← { userId, email }
    │       │   └── oauth/
    │       │       ├── CustomOAuth2UserService.java        ← OAuth2 사용자 DB 연동
    │       │       ├── OAuth2UserInfo.java                 ← 추상 인터페이스
    │       │       ├── KakaoUserInfo.java                  ← 카카오 응답 파싱
    │       │       ├── GoogleUserInfo.java                 ← 구글 응답 파싱
    │       │       ├── OAuth2AuthenticationSuccessHandler.java ← 토큰 발급 후 리다이렉트
    │       │       └── OAuth2AuthenticationFailureHandler.java ← 에러 URL 인코딩 후 리다이렉트
    │       ├── exception/
    │       │   ├── BusinessException.java             ← ErrorCode를 감싸는 런타임 예외
    │       │   ├── ErrorCode.java                     ← 에러 코드 enum (4xxx/5xxx 체계)
    │       │   └── GlobalExceptionHandler.java        ← @RestControllerAdvice
    │       ├── response/
    │       │   └── GlobalResponse.java                ← { status, message, data }
    │       └── util/
    │           ├── BaseEntity.java                    ← createdAt만 있는 base
    │           ├── BaseTimeEntity.java                ← createdAt + updatedAt
    │           ├── SecurityUtil.java                  ← SecurityContext에서 userId 추출
    │           └── HealthCheckController.java         ← GET /actuator/health
    │
    └── resources/
        ├── application.yml            ← 환경별 설정 (local / prod 프로필)
        └── static/
            ├── login.html             ← 로그인 페이지 + OAuth2 콜백 처리
            ├── home.html              ← 홈 (오늘의 뉴스 그리드)
            ├── news-detail.html       ← 뉴스 상세 + AI 요약 + 용어 팝오버
            ├── mypage.html            ← 마이페이지 (프로필/통계/카테고리/북마크)
            └── test.html              ← API 테스트 콘솔
```

---

## 3. API 목록

### Auth — `/api/auth`
| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/oauth2/authorization/kakao` | 불필요 | 카카오 OAuth2 로그인 시작 |
| GET | `/oauth2/authorization/google` | 불필요 | 구글 OAuth2 로그인 시작 |
| POST | `/api/auth/signup` | 불필요 | 이메일 회원가입 → TokenResponse |
| POST | `/api/auth/login` | 불필요 | 이메일 로그인 → TokenResponse |
| POST | `/api/auth/reissue` | 불필요 | Refresh Token → 새 Access/Refresh Token |
| POST | `/api/auth/logout` | 필요 | Access Token 블랙리스트 + Refresh Token 삭제 |
| DELETE | `/api/auth/withdraw` | 필요 | 회원 탈퇴 (soft delete) |

### User — `/api/users`
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/users/me` | 내 프로필 조회 |
| PATCH | `/api/users/me` | 닉네임 수정 |
| PUT | `/api/users/me/categories` | 관심 카테고리 교체 (기존 삭제 후 재삽입) |
| PUT | `/api/users/me/policy-profile` | 정책 추천용 프로필 저장 (나이/지역/취업/소득) |
| GET | `/api/users/me/learning-stats` | 학습 통계 (읽은뉴스 수 / 학습용어 수 / 연속학습일) |
| GET | `/api/users/me/bookmarks` | 북마크 뉴스 목록 (페이지네이션) |
| GET | `/api/users/me/learned-terms` | 학습한 용어 목록 |

### News — `/api/news`
| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/news` | 불필요 | 뉴스 목록 (`?category=&page=&size=`) |
| GET | `/api/news/today` | 불필요 | 오늘의 뉴스 최대 12건 (로그인 시 관심 카테고리 우선) |
| GET | `/api/news/{id}` | 불필요 | 뉴스 상세 (AI 요약 + 용어 하이라이트) |
| POST | `/api/news/{id}/read` | 필요 | 읽음 처리 (`?durationSeconds=`) |
| POST | `/api/news/{id}/bookmark` | 필요 | 북마크 토글 → `{ bookmarked: true/false }` |

### Term — `/api/terms`
| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/terms` | 불필요 | 전체 용어 목록 (난이도순, 페이지네이션) |
| GET | `/api/terms/{id}` | 불필요 | 용어 상세 조회 |
| POST | `/api/terms/{id}/learn` | 필요 | 용어 학습 완료 처리 |
| GET | `/api/terms/search?query=` | 불필요 | tsvector 전문 검색 |
| GET | `/api/terms/autocomplete?query=` | 불필요 | pg_trgm 유사 문자열 자동완성 |
| GET | `/api/terms/category?path=` | 불필요 | ltree 경로 하위 용어 조회 (예: `finance.stock`) |
| GET | `/api/terms/level?maxLevel=` | 불필요 | 난이도 이하 용어 조회 |

### Policy — `/api/policies`
| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/policies/recommend?userId=` | 불필요* | 프로필 기반 맞춤 정책 목록 |
| GET | `/api/policies/{id}` | 불필요 | 정책 상세 조회 |
| POST | `/api/policies/{id}/bookmark?userId=` | 불필요* | 정책 북마크 토글 |

> *TODO: `userId`를 `@RequestParam`으로 받고 있음. SecurityContext에서 추출하는 방식으로 전환 필요.

### Spending — `/api/spending`
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/spending` | 소비 기록 추가 (CREATED 이벤트 삽입) |
| PUT | `/api/spending/{id}` | 소비 기록 수정 (UPDATED 이벤트 삽입) |
| DELETE | `/api/spending/{id}` | 소비 기록 삭제 (DELETED 이벤트 삽입) |
| GET | `/api/spending/dashboard?month=` | 월별 소비 대시보드 (스냅샷 기반) |

> TODO: `userId`를 `@RequestParam`으로 받고 있음. SecurityContext 방식으로 전환 필요.

### Admin — `/api/admin` (ROLE_ADMIN 전용)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/admin/crawl/trigger` | 수동 크롤링 실행 |
| GET | `/api/admin/crawl/status` | 마지막 크롤링 상태 조회 |

### 기타
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/actuator/health` | 서버/DB/Redis 헬스 체크 |
| GET | `/api/guides/**` | 금융 가이드 콘텐츠 |

---

## 4. 설계 특이사항

### 4-1. Event Sourcing — 소비 관리
`spending_events` 테이블은 **INSERT ONLY**. 수정/삭제도 새 이벤트(UPDATED/DELETED)를 추가하는 방식.  
동일한 `spending_id`의 이벤트를 시간순으로 재생하면 현재 상태를 복원할 수 있어 이력 추적이 용이하다.  
대시보드 조회는 `spending_snapshots`(월별 집계 캐시)를 먼저 확인하고, 없으면 이벤트 테이블에서 직접 집계한다.

### 4-2. Temporal Versioning — 뉴스 AI 요약
`news_summaries` 테이블에 `valid_from` / `valid_to` 컬럼을 두어 AI 모델 업그레이드 시 이전 요약을 보존한다.  
현재 유효한 요약은 `valid_to IS NULL` 조건으로 조회하고, 새 버전 생성 시 기존 버전의 `valid_to`를 현재 시각으로 설정한다.

### 4-3. ltree + pgvector + pg_trgm — 금융 용어
`financial_terms.path`는 PostgreSQL `ltree` 타입으로 계층 구조를 표현한다 (예: `finance.stock.etf`).  
- **전문 검색**: `tsvector` + GIN 인덱스 → `@@` 연산자  
- **자동완성**: `pg_trgm` + GIN 인덱스 → `%` 유사도 연산자  
- **RAG 검색**: `pgvector` + HNSW 인덱스 → `<=>` 코사인 유사도 (AI 연동 후 활성화)

### 4-4. Soft Delete — User
`User` 엔티티에 `@SQLRestriction("deleted_at IS NULL")`을 적용해 탈퇴한 사용자를 조회에서 자동 제외한다.  
회원 탈퇴 시 `users.deleted_at`을 현재 시각으로 설정하고, Access Token 블랙리스트 등록 + Refresh Token 삭제를 동시에 처리한다.

### 4-5. OAuthAccount 분리
소셜 로그인 정보(`provider`, `provider_id`)를 `users` 테이블이 아닌 `oauth_accounts` 테이블로 분리했다.  
한 사용자가 카카오/구글을 모두 연동하는 멀티 소셜 로그인 시나리오를 지원하기 위함이다.

### 4-6. 공통 응답 포맷
모든 API는 `GlobalResponse<T>` 래퍼로 응답한다.
```json
{ "status": 200, "message": "ok", "data": { ... } }
{ "status": 201, "message": "created", "data": 42 }
{ "status": 4001, "message": "인증이 필요합니다", "data": null }
```

---

## 5. 주요 동작 흐름

### 5-1. OAuth2 로그인 흐름
```
[login.html]
  └─ 카카오/구글 버튼 클릭
       └─ GET /oauth2/authorization/{provider}
            └─ Spring Security가 세션에 인증요청 저장 (IF_REQUIRED 세션 정책)
                 └─ 소셜 로그인 페이지로 리다이렉트
                      └─ 사용자 인증 완료
                           └─ GET /login/oauth2/code/{provider} (콜백)
                                └─ CustomOAuth2UserService
                                │    ├─ OAuthAccount.provider + provider_id로 기존 계정 조회
                                │    ├─ 신규: User 생성 + OAuthAccount 생성 + DB 저장
                                │    └─ 기존: 기존 User 반환
                                └─ OAuth2AuthenticationSuccessHandler
                                     ├─ Access Token 발급 (15분, role 포함)
                                     ├─ Refresh Token 발급 (7일, Redis: refresh:{userId})
                                     └─ login.html?accessToken=...&refreshToken=... 리다이렉트
                                          └─ [login.html] 토큰 localStorage 저장 → home.html 이동
```

### 5-2. API 인증 흐름 (JWT)
```
클라이언트 요청
  └─ Authorization: Bearer {accessToken} 헤더
       └─ JwtAuthenticationFilter
            ├─ 토큰 유효성 검증 (서명 + 만료 시각)
            ├─ Redis blacklist:{token} 확인 (로그아웃 토큰 차단)
            ├─ Claims에서 userId, email, role 추출
            └─ SecurityContext에 CustomUserPrincipal 저장
                 └─ 컨트롤러에서 @AuthenticationPrincipal로 주입
```

### 5-3. 뉴스 크롤링 파이프라인
```
[수동: POST /api/admin/crawl/trigger]
[자동: 스케줄러 (CrawlerConfig)]
  └─ NewsProcessingService.processAll()
       └─ HankyungCrawlerService.crawl()
            ├─ Jsoup으로 한경 금융뉴스 목록 페이지 파싱
            └─ 각 기사 URL → CrawledArticleDto 변환
       └─ 기사별 처리
            ├─ existsBySourceUrl() 중복 체크 → 중복이면 스킵
            ├─ NewsArticle 저장 → news_articles 테이블
            ├─ AiServiceClient.process() (현재 stub 모드)
            │    ├─ stub=true: 더미 요약 텍스트 반환
            │    └─ stub=false: Node.js POST /process 호출
            └─ NewsSummary 저장 → news_summaries 테이블 (validTo = null)
```

### 5-4. 학습 통계 계산
```
[GET /api/users/me/learning-stats]
  └─ UserService.getLearningStats(userId)
       ├─ readNewsCount: reading_history WHERE user_id = ? → COUNT
       ├─ learnedTermCount: term_lookup_logs → COUNT(DISTINCT term_id)
       └─ consecutiveLearningDays:
            ├─ reading_history에서 user의 read_at 날짜 목록 조회 (DESC)
            ├─ 오늘부터 역순으로 연속된 날짜 카운트
            └─ 빈 날 발견 시 중단 → 반환
```

---

## 6. 프론트엔드 정적 페이지

### login.html
- 카카오/구글 로그인 버튼
- URL `?accessToken=&refreshToken=` 감지 → localStorage 저장 후 home.html 이동
- `?error=` 파라미터 감지 → 토스트 에러 메시지 표시
- 이미 로그인된 경우 home.html 자동 이동

### home.html
- `/api/news/today` 호출 → 3열 카드 그리드 렌더링
- 카테고리 필터 칩 (경제/투자/보험/대출/정책)
- 카드 클릭 → `news-detail.html?id=`
- 북마크 토글 버튼
- 로그인 상태: 닉네임 표시 + 마이페이지 링크

### news-detail.html
- `/api/news/{id}` 호출 → 제목/본문/AI 요약 렌더링
- 본문 내 금융 용어 하이라이트 (마우스 오버 팝오버)
- 자동 읽음 처리 (페이지 진입 시)
- 북마크 토글

### mypage.html
- 프로필 카드 (아바타 이니셜, 닉네임, 이메일)
- 학습 통계 3카드 (읽은 뉴스 / 학습 용어 / 연속 학습일)
- 관심 카테고리 칩 선택 + 저장
- 북마크 뉴스 목록 (더보기 페이지네이션)
- 프로필 수정 모달 (닉네임 + 생년월일/지역/취업상태/소득구간)
- 로그아웃

---

## 7. 환경 설정

### application.yml 핵심 설정
```yaml
server.port: 8080

spring.datasource:
  url: jdbc:postgresql://localhost:5432/youthletter
  username: ${DB_USERNAME:youthletter}
  password: ${DB_PASSWORD:youthletter1234}

spring.data.redis:
  host: localhost
  port: 6379

jwt:
  access-token-expiration:  900000    # 15분
  refresh-token-expiration: 604800000 # 7일

oauth2:
  redirect-uri: http://localhost:8080/login.html   # OAuth2 콜백 도착지

ai-service:
  base-url: http://localhost:3000
  use-stub: true    # Node.js AI 서버 완성 후 false로 변경
```

### Redis 키 규칙
| 키 | 값 | TTL |
|----|-----|-----|
| `refresh:{userId}` | Refresh Token 문자열 | 7일 |
| `blacklist:{accessToken}` | `"logout"` | 토큰 남은 만료 시간 |

### CORS 허용 Origin
- `http://localhost:3000` (AI 서비스 / 테스트)
- `http://localhost:3001` (Next.js 프론트엔드)
- `http://localhost:8080` (Spring Boot 내 static HTML 테스트)

---

## 8. 에러 코드 체계

```
4001 UNAUTHORIZED       인증 필요
4002 TOKEN_EXPIRED      토큰 만료
4003 INVALID_TOKEN      유효하지 않은 토큰
4004 ACCESS_DENIED      권한 없음

4100 USER_NOT_FOUND     사용자 없음
4101 DUPLICATE_EMAIL    이메일 중복
4102 DUPLICATE_NICKNAME 닉네임 중복
4103 INVALID_CREDENTIALS 이메일/비밀번호 불일치
4104 OAUTH_ONLY_ACCOUNT 소셜 로그인 전용 계정

4200 ARTICLE_NOT_FOUND  뉴스 기사 없음
4300 TERM_NOT_FOUND     용어 없음
4400 POLICY_NOT_FOUND   정책 없음
4401 ALREADY_BOOKMARKED 이미 즐겨찾기

4500 SESSION_NOT_FOUND  챗 세션 없음

5000 INTERNAL_ERROR     서버 내부 오류
5001 AI_SERVICE_ERROR   AI 서비스 연결 불가
5002 AI_SERVICE_TIMEOUT AI 응답 시간 초과
5003 CRAWLING_ERROR     크롤링 오류
```

---

## 9. 알려진 이슈 / 추후 처리 필요 사항

| 항목 | 내용 |
|------|------|
| 한경 크롤링 URL | `https://www.hankyung.com/finance` 404 반환 → 실제 URL로 교체 필요 |
| AI 서비스 연동 | `use-stub: true` 상태 → 혜린 Node.js 서버 완성 후 `false`로 전환 |
| Policy/Spending userId | `@RequestParam Long userId` 방식 사용 중 → `@AuthenticationPrincipal`로 전환 필요 |
| 레거시 엔티티 | `News.java`, `NewsRepository.java` 미사용 상태로 잔존 → 정리 필요 |
| AI 챗봇 | `ChatSession`, `ChatMessage`, `AgentFeedback` 엔티티만 있고 서비스/컨트롤러 없음 |
| Admin 계정 | DB에서 수동으로 `UPDATE users SET role = 'ADMIN' WHERE email = '...'` 필요 |
| 정책 데이터 | 실제 정책 데이터 적재 필요 (공공 API 연동 또는 초기 데이터 SQL) |
