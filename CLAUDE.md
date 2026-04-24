# CLAUDE.md — 유스레터 백엔드 개발 가이드

> Claude Code가 이 프로젝트를 이어받아 개발할 때 참고하는 문서.  
> 모든 코드는 이 문서의 컨벤션·구조·기술 스펙을 따른다.

---

## 1. 프로젝트 개요

**서비스명:** 유스레터 (Youth Letter) — 청년 금융 AI 플랫폼  
**역할:** Spring Boot Main API 서버 (백엔드 전담)  
**포트:** 8080  
**담당:** 정다은

### 서버 구성 (전체 시스템)
| 서버 | 기술 | 포트 | 담당 |
|------|------|------|------|
| Main API | Spring Boot 3.4.4 / Java 17 | 8080 | 정다은 |
| AI Service | Node.js Express | 3000 | 김혜린 |
| Frontend | Next.js 14 | 3001 | 안소윤 |
| Database | PostgreSQL 16 + pgvector + ltree | 5432 | 공동 |
| Cache | Redis 7 | 6379 | 공동 |

---

## 2. 기술 스택 및 의존성

```gradle
// build.gradle (핵심 의존성)
dependencies {
    // Spring
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.3'

    // QueryDSL
    implementation 'com.querydsl:querydsl-jpa:5.1.0:jakarta'
    annotationProcessor 'com.querydsl:querydsl-apt:5.1.0:jakarta'

    // Jsoup
    implementation 'org.jsoup:jsoup:1.17.2'

    // DB
    implementation 'org.postgresql:postgresql'
    implementation 'org.flywaydb:flyway-core'

    // Util
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
}
```

---

## 3. 패키지 구조

```
src/main/java/com/youthletter/
├── YouthletterApplication.java
│
├── auth/
│   ├── controller/   AuthController.java
│   ├── service/      AuthService.java, CustomOAuth2UserService.java
│   ├── repository/   UserRepository.java
│   ├── dto/          SignupRequest.java, LoginRequest.java, TokenResponse.java
│   ├── entity/       User.java
│   ├── jwt/          JwtTokenProvider.java, JwtAuthenticationFilter.java
│   └── handler/      OAuth2SuccessHandler.java, OAuth2FailureHandler.java
│
├── news/
│   ├── controller/   NewsController.java
│   ├── service/      NewsService.java, NewsProcessingService.java
│   ├── repository/   NewsRepository.java, NewsReadRepository.java, BookmarkRepository.java
│   ├── dto/          NewsListResponse.java, NewsDetailResponse.java
│   └── entity/       News.java, NewsRead.java, NewsBookmark.java
│
├── crawler/
│   ├── HankyungCrawlerService.java
│   └── CrawlerConfig.java
│
├── term/
│   ├── controller/   TermController.java
│   ├── service/      TermService.java
│   ├── repository/   TermRepository.java, TermLearnRepository.java
│   ├── dto/          TermResponse.java, TermDetailResponse.java
│   └── entity/       Term.java, UserTermLearn.java
│
├── policy/
│   ├── controller/   PolicyController.java
│   ├── service/      PolicyService.java, PolicyScheduler.java
│   ├── repository/   PolicyRepository.java, PolicyQueryRepository.java
│   ├── dto/          PolicyListResponse.java, PolicyDetailResponse.java, PolicyRecommendFilter.java
│   └── entity/       Policy.java, PolicyBookmark.java
│
├── spending/
│   ├── controller/   SpendingController.java
│   ├── service/      SpendingService.java
│   ├── repository/   SpendingRepository.java, BudgetRepository.java
│   ├── dto/          SpendingRequest.java, SpendingResponse.java, SpendingSummaryResponse.java
│   └── entity/       Spending.java, SpendingBudget.java
│
├── stock/
│   ├── controller/   StockController.java
│   ├── service/      StockService.java
│   ├── repository/   WatchlistRepository.java
│   ├── dto/          StockResponse.java
│   └── entity/       StockWatchlist.java
│
├── notification/
│   ├── controller/   NotificationController.java
│   ├── service/      NotificationService.java
│   ├── repository/   NotificationRepository.java
│   ├── dto/          NotificationResponse.java
│   └── entity/       Notification.java
│
├── user/
│   ├── controller/   UserController.java
│   ├── service/      UserService.java
│   ├── dto/          ProfileResponse.java, ProfileUpdateRequest.java, PolicyProfileRequest.java
│   └── entity/       UserPolicyProfile.java, UserCategory.java
│
├── admin/
│   └── controller/   AdminController.java
│
├── ai/
│   ├── AiServiceClient.java          // WebClient 기반 AI 서비스 호출
│   └── dto/
│       ├── AiSummaryResponse.java
│       ├── AiNerResponse.java
│       └── AiInsightResponse.java
│
└── common/
    ├── config/
    │   ├── SecurityConfig.java
    │   ├── RedisConfig.java
    │   ├── WebClientConfig.java
    │   ├── QueryDslConfig.java
    │   └── CrawlerConfig.java
    ├── exception/
    │   ├── GlobalExceptionHandler.java
    │   ├── CustomException.java
    │   └── ErrorCode.java
    ├── response/
    │   └── ApiResponse.java           // 공통 응답 wrapper
    └── util/
        └── SecurityUtil.java          // 현재 로그인 유저 ID 추출
```

---

## 4. 공통 규칙

### 4-1. API 응답 형식
모든 API는 아래 wrapper로 응답한다.

```java
// ApiResponse.java
@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "success", data);
    }
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }
    public static ApiResponse<Void> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
```

### 4-2. 예외 처리
```java
// ErrorCode.java — 모든 에러 코드 여기에 정의
public enum ErrorCode {
    USER_NOT_FOUND("U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL("U002", "이미 사용 중인 이메일입니다."),
    INVALID_TOKEN("A001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN("A002", "만료된 토큰입니다."),
    NEWS_NOT_FOUND("N001", "뉴스를 찾을 수 없습니다."),
    TERM_NOT_FOUND("T001", "용어를 찾을 수 없습니다."),
    POLICY_NOT_FOUND("P001", "정책을 찾을 수 없습니다."),
    AI_SERVICE_UNAVAILABLE("AI001", "AI 서비스 연결 실패"),
    SPENDING_NOT_FOUND("S001", "소비 내역을 찾을 수 없습니다.");
    ...
}

// GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }
}
```

### 4-3. 인증 방식
- 모든 보호된 API: `Authorization: Bearer {accessToken}` 헤더 필수
- `SecurityUtil.getCurrentUserId()` 로 현재 로그인 유저 ID 추출
- `/api/auth/**`, `/api/health`, `/api/news` (GET), `/api/policies` (GET) 는 인증 불필요

### 4-4. DB 컨벤션
- 테이블명: snake_case (예: `user_term_learn`)
- PK: `id BIGSERIAL PRIMARY KEY`
- 시간 컬럼: `created_at TIMESTAMPTZ DEFAULT now()`, `updated_at TIMESTAMPTZ DEFAULT now()`
- soft delete: `deleted_at TIMESTAMPTZ NULL` (null이면 활성)
- Flyway 마이그레이션: `V{순번}__{설명}.sql` (예: `V1__create_users_table.sql`)

### 4-5. AiServiceClient stub 모드
```java
// AiServiceClient.java
@Component
public class AiServiceClient {
    
    @Value("${ai.service.stub-mode:true}")
    private boolean stubMode;

    // stub 모드면 mock 데이터 반환, false면 실제 Node.js 호출
    public AiSummaryResponse getSummary(Long newsId) {
        if (stubMode) {
            return AiSummaryResponse.stub(); // mock 3줄 요약
        }
        // 실제 WebClient 호출 (AI 연동 완료 후 구현)
        return callAiService("/summarize", newsId, AiSummaryResponse.class);
    }
}
```

---

## 5. DB 스키마 (Flyway 마이그레이션 순서)

### V1 — users
```sql
CREATE EXTENSION IF NOT EXISTS pgvector;
CREATE EXTENSION IF NOT EXISTS ltree;

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) UNIQUE NOT NULL,
    password    VARCHAR(255),                   -- 소셜 로그인은 null
    nickname    VARCHAR(100) NOT NULL,
    provider    VARCHAR(20) DEFAULT 'LOCAL',     -- LOCAL, KAKAO, GOOGLE
    provider_id VARCHAR(255),
    profile_image_url TEXT,
    role        VARCHAR(20) DEFAULT 'ROLE_USER',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
```

### V2 — user_policy_profile
```sql
CREATE TABLE user_policy_profile (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT UNIQUE REFERENCES users(id),
    age             INT,
    region          VARCHAR(100),               -- 시/도 단위
    employment_status VARCHAR(50),             -- EMPLOYED, UNEMPLOYED, STUDENT
    income_level    VARCHAR(50),               -- LOW, MIDDLE, HIGH
    updated_at      TIMESTAMPTZ DEFAULT now()
);
```

### V3 — user_categories
```sql
CREATE TABLE user_categories (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    category    VARCHAR(50) NOT NULL,           -- ECONOMY, INVEST, INSURANCE, LOAN, POLICY, YOUTH
    UNIQUE(user_id, category)
);
```

### V4 — news
```sql
CREATE TABLE news (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(500) NOT NULL,
    content         TEXT NOT NULL,
    original_url    VARCHAR(1000) UNIQUE NOT NULL,
    source          VARCHAR(100),               -- 한경, 매경 등
    category        VARCHAR(50),
    thumbnail_url   TEXT,
    ai_summary      TEXT,                       -- AI 3줄 요약 (nullable, stub 시 null)
    ner_terms       JSONB,                      -- [{"term":"ETF","description":"..."}]
    embedding       vector(1536),               -- pgvector (nullable)
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_news_category ON news(category);
CREATE INDEX idx_news_published_at ON news(published_at DESC);
CREATE INDEX idx_news_embedding ON news USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

### V5 — news_reads, news_bookmarks
```sql
CREATE TABLE news_reads (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    news_id     BIGINT REFERENCES news(id),
    read_at     TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, news_id)
);

CREATE TABLE news_bookmarks (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    news_id     BIGINT REFERENCES news(id),
    created_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, news_id)
);
```

### V6 — terms
```sql
CREATE TABLE terms (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) UNIQUE NOT NULL,
    description TEXT NOT NULL,
    category    VARCHAR(50),
    search_vector TSVECTOR,                     -- 전문 검색용
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_terms_search ON terms USING gin(search_vector);

-- tsvector 자동 업데이트 트리거
CREATE TRIGGER terms_search_update
BEFORE INSERT OR UPDATE ON terms
FOR EACH ROW EXECUTE FUNCTION
tsvector_update_trigger(search_vector, 'pg_catalog.simple', name, description);
```

### V7 — user_term_learns
```sql
CREATE TABLE user_term_learns (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    term_id     BIGINT REFERENCES terms(id),
    learned_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, term_id)
);
```

### V8 — policies
```sql
CREATE TABLE policies (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    support_content TEXT,
    eligibility     TEXT,
    application_url TEXT,
    category        VARCHAR(50),               -- HOUSING, EMPLOYMENT, EDUCATION, FINANCE
    region          VARCHAR(100),              -- 전국 또는 시/도
    min_age         INT,
    max_age         INT,
    employment_status VARCHAR(50),
    income_level    VARCHAR(50),
    deadline        DATE,
    external_id     VARCHAR(200) UNIQUE,       -- 공공 API ID (upsert용)
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE policy_bookmarks (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    policy_id   BIGINT REFERENCES policies(id),
    created_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, policy_id)
);
```

### V9 — spendings, spending_budgets
```sql
CREATE TYPE spending_category AS ENUM 
    ('FOOD', 'TRANSPORT', 'HOUSING', 'ENTERTAINMENT', 'SHOPPING', 'HEALTH', 'EDUCATION', 'ETC');

CREATE TABLE spendings (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    amount      INT NOT NULL,
    category    spending_category NOT NULL,
    memo        VARCHAR(500),
    spent_at    DATE NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE TABLE spending_budgets (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT UNIQUE REFERENCES users(id),
    monthly_budget INT NOT NULL,
    updated_at  TIMESTAMPTZ DEFAULT now()
);
```

### V10 — stock_watchlists
```sql
CREATE TABLE stock_watchlists (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    ticker      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, ticker)
);
```

### V11 — notifications
```sql
CREATE TYPE notification_type AS ENUM ('POLICY_DEADLINE', 'NEWS_RECOMMEND', 'SYSTEM');

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES users(id),
    type            notification_type NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            TEXT,
    related_id      BIGINT,                     -- policy_id 또는 news_id
    is_read         BOOLEAN DEFAULT false,
    created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, is_read, created_at DESC);
```

---

## 6. 모듈별 구현 상세

---

### MODULE 1: Auth

**파일 목록**
```
auth/entity/User.java
auth/repository/UserRepository.java
auth/dto/SignupRequest.java
auth/dto/LoginRequest.java
auth/dto/TokenResponse.java
auth/service/AuthService.java
auth/service/CustomOAuth2UserService.java
auth/controller/AuthController.java
auth/jwt/JwtTokenProvider.java
auth/jwt/JwtAuthenticationFilter.java
auth/handler/OAuth2SuccessHandler.java
common/config/SecurityConfig.java
common/config/RedisConfig.java
```

**User Entity**
```java
@Entity @Table(name = "users")
@Getter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    private String password;        // BCrypt, 소셜로그인은 null
    
    @Column(nullable = false)
    private String nickname;
    
    private String provider;        // LOCAL, KAKAO, GOOGLE
    private String providerId;
    private String profileImageUrl;
    private String role;            // ROLE_USER, ROLE_ADMIN
    private LocalDateTime deletedAt;
    
    // 소셜 로그인 정보 업데이트용
    public User updateOAuth(String nickname, String profileImageUrl) { ... }
}
```

**JwtTokenProvider 주요 메서드**
```java
public String generateAccessToken(Long userId)   // 15분 만료
public String generateRefreshToken(Long userId)  // 7일 만료
public Long getUserId(String token)
public boolean validateToken(String token)
```

**Redis 키 규칙**
```
RT:{userId}       → Refresh Token 값 (TTL 7일)
BL:{token}        → 블랙리스트 Access Token (로그아웃 시, TTL = 남은 만료 시간)
```

**AuthController 엔드포인트**
```
POST /api/auth/signup       → AuthService.signup(SignupRequest)
POST /api/auth/login        → AuthService.login(LoginRequest) → TokenResponse
POST /api/auth/reissue      → AuthService.reissue(refreshToken) → TokenResponse
POST /api/auth/logout       → AuthService.logout(accessToken, userId)
DELETE /api/auth/withdraw   → AuthService.withdraw(userId)
GET  /api/auth/oauth2/kakao → OAuth2 자동 처리 (Spring Security)
GET  /api/auth/oauth2/google→ OAuth2 자동 처리 (Spring Security)
```

---

### MODULE 2: User Profile

**파일 목록**
```
user/controller/UserController.java
user/service/UserService.java
user/dto/ProfileResponse.java
user/dto/ProfileUpdateRequest.java
user/dto/PolicyProfileRequest.java
user/dto/LearningStatsResponse.java
user/entity/UserPolicyProfile.java
user/entity/UserCategory.java
```

**UserController 엔드포인트**
```
GET    /api/users/me                    → 내 프로필 조회
PATCH  /api/users/me                    → 닉네임, 프로필 이미지 수정
PUT    /api/users/me/categories         → 관심 카테고리 교체 (기존 삭제 후 재삽입)
PUT    /api/users/me/policy-profile     → 정책 추천용 프로필 저장 (upsert)
GET    /api/users/me/learning-stats     → 읽은 뉴스 수, 학습 용어 수, 연속 학습일
GET    /api/users/me/bookmarks          → 북마크 뉴스 목록 (페이지네이션)
GET    /api/users/me/learned-terms      → 학습한 용어 목록
```

**LearningStatsResponse 구조**
```java
public record LearningStatsResponse(
    long readNewsCount,
    long learnedTermCount,
    int consecutiveDays        // 연속 학습일 (오늘 기준 역산)
) {}
```

---

### MODULE 3: News

**파일 목록**
```
news/entity/News.java
news/entity/NewsRead.java
news/entity/NewsBookmark.java
news/repository/NewsRepository.java
news/repository/NewsReadRepository.java
news/repository/NewsBookmarkRepository.java
news/service/NewsService.java
news/service/NewsProcessingService.java
news/dto/NewsListResponse.java
news/dto/NewsDetailResponse.java
news/controller/NewsController.java
crawler/HankyungCrawlerService.java
crawler/CrawlerConfig.java
```

**News Entity**
```java
@Entity @Table(name = "news")
public class News {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(unique = true)
    private String originalUrl;
    private String source;
    private String category;
    private String thumbnailUrl;
    @Column(columnDefinition = "TEXT")
    private String aiSummary;          // nullable
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<NerTerm> nerTerms;    // nullable
    // embedding은 native query로만 처리 (JPA 미지원)
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
```

**NewsRepository — native query 포함**
```java
public interface NewsRepository extends JpaRepository<News, Long> {
    
    // 카테고리 필터 + 날짜 정렬 (QueryDSL 또는 JPQL)
    Page<News> findByCategoryAndDeletedAtIsNull(String category, Pageable pageable);
    
    // 중복 체크
    boolean existsByOriginalUrl(String originalUrl);
    
    // 오늘의 뉴스 (관심 카테고리 우선)
    @Query(value = """
        SELECT * FROM news
        WHERE published_at >= CURRENT_DATE
        ORDER BY
            CASE WHEN category = ANY(:categories) THEN 0 ELSE 1 END,
            published_at DESC
        LIMIT 12
        """, nativeQuery = true)
    List<News> findTodayNews(@Param("categories") String[] categories);
    
    // 벡터 유사도 검색 (AI 연동 후 사용)
    @Query(value = """
        SELECT * FROM news
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<News> findSimilarNews(@Param("embedding") String embedding, @Param("limit") int limit);
}
```

**NewsController 엔드포인트**
```
GET  /api/news                      → 뉴스 목록 (category, date, keyword, page, size 쿼리 파라미터)
GET  /api/news/today                → 오늘의 뉴스 12건
GET  /api/news/{newsId}             → 뉴스 상세 (aiSummary, nerTerms 포함, stub 시 mock)
POST /api/news/{newsId}/read        → 읽음 처리
POST /api/news/{newsId}/bookmark    → 북마크 토글 (있으면 삭제, 없으면 추가)
```

**NewsListResponse 구조**
```java
public record NewsListResponse(
    Long id,
    String title,
    String category,
    String source,
    String thumbnailUrl,
    String aiSummary,          // 3줄 요약 첫 줄만 (목록용)
    LocalDateTime publishedAt,
    boolean isBookmarked       // 로그인 유저 기준
) {}
```

**HankyungCrawlerService**
```java
@Service
@RequiredArgsConstructor
public class HankyungCrawlerService {

    private final NewsProcessingService newsProcessingService;

    // 07:00, 12:00, 18:00 자동 실행
    @Scheduled(cron = "0 0 7,12,18 * * *")
    public void crawl() {
        // 1. Jsoup으로 한경 금융 뉴스 목록 페이지 파싱
        // 2. 각 기사 URL 추출
        // 3. existsByOriginalUrl 중복 체크
        // 4. 본문 파싱 → News 엔티티 생성
        // 5. NewsProcessingService.process(news) 호출
    }
}

@Service
public class NewsProcessingService {
    public void process(News news) {
        // 1. newsRepository.save(news)
        // 2. aiServiceClient.getSummary(news.getId())  → stub 모드 시 mock
        // 3. aiServiceClient.getNer(news.getId())      → stub 모드 시 mock
        // 4. 결과 업데이트 저장
    }
}
```

---

### MODULE 4: Term Dictionary

**파일 목록**
```
term/entity/Term.java
term/entity/UserTermLearn.java
term/repository/TermRepository.java
term/repository/TermLearnRepository.java
term/service/TermService.java
term/dto/TermResponse.java
term/dto/TermDetailResponse.java
term/controller/TermController.java
```

**TermController 엔드포인트**
```
GET  /api/terms                     → 전체 용어 목록 (category 필터)
GET  /api/terms/search?q=           → 용어명 전문 검색 (tsvector)
GET  /api/terms/{termId}            → 용어 상세 + 관련 뉴스 목록
POST /api/terms/{termId}/learn      → 학습 완료 처리 (upsert)
```

**TermRepository — tsvector 검색**
```java
@Query(value = """
    SELECT * FROM terms
    WHERE search_vector @@ plainto_tsquery('simple', :query)
    ORDER BY ts_rank(search_vector, plainto_tsquery('simple', :query)) DESC
    """, nativeQuery = true)
List<Term> searchByKeyword(@Param("query") String query);
```

---

### MODULE 5: Policy

**파일 목록**
```
policy/entity/Policy.java
policy/entity/PolicyBookmark.java
policy/repository/PolicyRepository.java
policy/repository/PolicyQueryRepository.java    // QueryDSL
policy/service/PolicyService.java
policy/service/PolicyScheduler.java
policy/dto/PolicyListResponse.java
policy/dto/PolicyDetailResponse.java
policy/dto/PolicyRecommendFilter.java
policy/controller/PolicyController.java
```

**PolicyController 엔드포인트**
```
GET  /api/policies                  → 정책 목록 (category, region, page, size)
GET  /api/policies/{policyId}       → 정책 상세
GET  /api/policies/recommend        → 프로필 기반 맞춤 추천 (로그인 필수)
POST /api/policies/{policyId}/bookmark → 북마크 토글
```

**PolicyQueryRepository — QueryDSL 동적 쿼리**
```java
public List<Policy> findByProfile(UserPolicyProfile profile) {
    return queryFactory
        .selectFrom(policy)
        .where(
            ageCondition(profile.getAge()),           // min_age <= age <= max_age
            regionCondition(profile.getRegion()),      // 전국 OR 해당 지역
            employmentCondition(profile.getEmploymentStatus()),
            incomeCondition(profile.getIncomeLevel()),
            policy.deadline.goe(LocalDate.now())       // 마감 안 된 정책만
        )
        .orderBy(policy.deadline.asc())
        .fetch();
}
```

**PolicyScheduler**
```java
@Scheduled(cron = "0 0 9 * * *")   // 매일 09:00
public void sendDeadlineNotifications() {
    // deadline이 D-7, D-3, D-1인 정책 조회
    // 해당 정책 북마크한 유저에게 Notification 생성
}
```

---

### MODULE 6: Spending

**파일 목록**
```
spending/entity/Spending.java
spending/entity/SpendingBudget.java
spending/repository/SpendingRepository.java
spending/repository/BudgetRepository.java
spending/service/SpendingService.java
spending/dto/SpendingRequest.java
spending/dto/SpendingResponse.java
spending/dto/SpendingSummaryResponse.java
spending/dto/SpendingTrendResponse.java
spending/controller/SpendingController.java
```

**SpendingController 엔드포인트**
```
POST   /api/spending                     → 소비 내역 등록
PATCH  /api/spending/{spendingId}        → 소비 내역 수정
DELETE /api/spending/{spendingId}        → 소비 내역 삭제 (soft delete)
GET    /api/spending?year=&month=        → 월별 소비 목록
GET    /api/spending/summary?year=&month=→ 카테고리별 집계
GET    /api/spending/trend               → 최근 6개월 트렌드
POST   /api/spending/budget              → 예산 설정 (upsert)
GET    /api/spending/budget/status       → 이번 달 예산 달성률
POST   /api/spending/ai-insight          → AI 인사이트 (stub)
```

**SpendingRepository — 윈도우 함수 native query**
```java
// 카테고리별 집계
@Query(value = """
    SELECT category, SUM(amount) as total
    FROM spendings
    WHERE user_id = :userId
      AND EXTRACT(YEAR FROM spent_at) = :year
      AND EXTRACT(MONTH FROM spent_at) = :month
      AND deleted_at IS NULL
    GROUP BY category
    """, nativeQuery = true)
List<Object[]> sumByCategory(@Param("userId") Long userId,
                              @Param("year") int year,
                              @Param("month") int month);

// 최근 6개월 트렌드 (LAG 윈도우 함수)
@Query(value = """
    SELECT
        TO_CHAR(month_date, 'YYYY-MM') as month,
        total_amount,
        LAG(total_amount) OVER (ORDER BY month_date) as prev_amount
    FROM (
        SELECT DATE_TRUNC('month', spent_at) as month_date,
               SUM(amount) as total_amount
        FROM spendings
        WHERE user_id = :userId AND deleted_at IS NULL
          AND spent_at >= CURRENT_DATE - INTERVAL '6 months'
        GROUP BY month_date
    ) sub
    ORDER BY month_date
    """, nativeQuery = true)
List<Object[]> findMonthlyTrend(@Param("userId") Long userId);
```

---

### MODULE 7: Stock

**파일 목록**
```
stock/entity/StockWatchlist.java
stock/repository/WatchlistRepository.java
stock/service/StockService.java
stock/dto/StockResponse.java
stock/controller/StockController.java
```

**StockController 엔드포인트**
```
GET    /api/stocks/{ticker}              → 시세 조회 (Redis 5분 캐시)
POST   /api/stocks/watchlist             → 관심 종목 추가
GET    /api/stocks/watchlist             → 관심 종목 목록
DELETE /api/stocks/watchlist/{ticker}    → 관심 종목 삭제
```

**Redis 캐시 키 규칙**
```
STOCK:{ticker}    → 주가 데이터 JSON (TTL 5분)
```

**외부 주가 API:** 한국투자증권 API 또는 Yahoo Finance API 사용  
실제 API Key는 `application.yml` `stock.api.key` 에서 주입

---

### MODULE 8: Notification

**파일 목록**
```
notification/entity/Notification.java
notification/repository/NotificationRepository.java
notification/service/NotificationService.java
notification/dto/NotificationResponse.java
notification/controller/NotificationController.java
```

**NotificationController 엔드포인트**
```
GET   /api/notifications                       → 내 알림 목록 (읽지 않은 것 우선)
PATCH /api/notifications/{notiId}/read         → 단건 읽음
PATCH /api/notifications/read-all              → 전체 읽음
```

---

### MODULE 9: Admin

**파일 목록**
```
admin/controller/AdminController.java
```

**AdminController 엔드포인트**
```
POST /api/admin/crawl           → 크롤링 수동 실행 (ROLE_ADMIN만)
GET  /api/admin/crawl/status    → 크롤링 상태 조회
POST /api/admin/ai-stub         → stub 모드 토글 (개발 환경)
GET  /api/health                → DB + Redis health check
```

---

## 7. application.yml 구조

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/youthletter
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate          # Flyway가 DDL 관리
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
            ...
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}

jwt:
  secret: ${JWT_SECRET}
  access-expiration: 900000        # 15분 (ms)
  refresh-expiration: 604800000    # 7일 (ms)

ai:
  service:
    url: http://localhost:3000
    stub-mode: true                # 개발 중엔 true, AI 연동 완료 후 false

stock:
  api:
    key: ${STOCK_API_KEY}
    url: https://...

crawler:
  targets:
    - https://www.hankyung.com/finance
  cron: "0 0 7,12,18 * * *"
```

---

## 8. 코딩 규칙 요약

1. **엔티티는 절대 컨트롤러까지 올라오지 않는다** — 반드시 DTO로 변환
2. **@Transactional은 서비스 레이어에만** — 컨트롤러, 레포지토리에 달지 않는다
3. **`@RequiredArgsConstructor` + `final` 필드로 생성자 주입** — `@Autowired` 사용 금지
4. **페이지네이션 기본값:** `page=0, size=20`
5. **모든 날짜/시간:** `LocalDateTime` (UTC 기준, DB는 TIMESTAMPTZ)
6. **민감 정보:** `application.yml` 환경 변수로만 관리, 코드에 하드코딩 금지
7. **native query 결과 Object[]는 바로 record/DTO로 변환** — 서비스에서 raw 처리 금지
