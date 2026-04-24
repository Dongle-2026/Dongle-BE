# 유스레터 (Youth Letter) — 2주차 개발 컨텍스트

## 프로젝트 한줄 요약
청년(19~34세) 대상 금융 AI 플랫폼. 뉴스 크롤링 → AI 요약/NER/임베딩 → SSR 뉴스 목록까지 이번 주 완성 목표.

---

## 팀 구성 & 담당
| 이름 | 역할 | 기술 |
|------|------|------|
| 정다은 | 백엔드 / 총괄 | Spring Boot 3.4.4 (Java 17) |
| 김혜린 | AI Service | Node.js + LangChain |
| 안소윤 | 프론트엔드 | Next.js 14 |

---

## 기술 스택
- **Backend**: Spring Boot 3.4.4, Java 17, JPA, QueryDSL, WebClient
- **AI Service**: Node.js (Express), LangChain, OpenAI (GPT-4o-mini, ada-002)
- **Frontend**: Next.js 14, React 18, Zustand, Recharts
- **DB**: PostgreSQL + pgvector(벡터검색) + ltree(계층분류) + tsvector(전문검색)
- **Cache**: Redis
- **Infra**: Docker Compose, GitHub Actions CI/CD
- **패키지 기준**: `com.youthletter`

---

## 이번 주 할 일 (2주차)

### 정다은 (백엔드)
- [x] CrawledArticleDto, AiProcessRequest/Response DTO 작성
- [x] HankyungCrawlerService (Jsoup 파싱)
- [x] AiServiceClient (WebClient + stub 모드)
- [x] NewsProcessingService (@Scheduled 크론 + 파이프라인)
- [x] NewsRepository (pgvector 네이티브 쿼리)
- [x] CrawlerConfig (@EnableScheduling + WebClient Bean)
- [ ] **News 엔티티 작성** ← 아직 없음
- [ ] **application.yml에 ai-service 설정 추가** ← 아직 없음
- [ ] **수동 트리거 엔드포인트** (`POST /api/admin/crawl/trigger`) ← 아직 없음
- [ ] **news 테이블 `original_url` UNIQUE 제약 확인**

### 김혜린 (AI Service)
- [ ] `POST /api/ai/process` 구현 (요약 + NER + 임베딩 한번에)
- [ ] GPT-4o-mini 기반 3줄 요약
- [ ] Prompt-based NER 금융 용어 추출
- [ ] ada-002 임베딩 생성

### 안소윤 (프론트엔드)
- [ ] 뉴스 목록 SSR 페이지 (`/news`)

---

## 파일 구조 (백엔드)

```
src/main/java/com/youthletter/
├── config/
│   └── CrawlerConfig.java          ← @EnableScheduling + WebClient Bean
├── crawler/
│   ├── dto/CrawledArticleDto.java  ← Jsoup 파싱 결과 DTO
│   └── service/HankyungCrawlerService.java
├── news/
│   ├── client/
│   │   ├── AiServiceClient.java    ← WebClient (stub 모드 포함)
│   │   └── dto/
│   │       ├── AiProcessRequest.java
│   │       └── AiProcessResponse.java
│   ├── domain/
│   │   └── News.java               ← ⚠️ 아직 없음, 작성 필요
│   ├── repository/
│   │   └── NewsRepository.java     ← pgvector 네이티브 쿼리 포함
│   └── service/
│       └── NewsProcessingService.java  ← @Scheduled 크론
src/main/resources/
└── application.yml                 ← ai-service 설정 추가 필요
```

---

## 구현 완료된 핵심 코드

### HankyungCrawlerService 핵심 로직
```java
// 목록 페이지에서 기사 URL 수집
Elements links = doc.select("ul.news-list a[href], div.article-list a[href]");
// 실패 시 fallback
if (links.isEmpty()) links = doc.select("a[href*='/article/']");

// 본문 추출 selector 후보 (순서대로 시도)
List.of("div#articletxt", "div.article-body", "div.article-cont", "div#newsContent")
```

### AiServiceClient stub 모드
```java
@Value("${ai-service.use-stub:true}")
private boolean useStub;
// true면 AI Service 없이도 크롤링→DB 저장 흐름 테스트 가능
// 혜린이 API 완성 후 application.yml에서 false로 전환
```

### NewsRepository pgvector 쿼리
```java
// 임베딩 저장 (vector 타입은 JPA 자동 매핑 불가 → 네이티브 쿼리)
@Query(value = "UPDATE news SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
void updateEmbedding(@Param("id") Long id, @Param("embedding") List<Float> embedding);

// 유사도 검색 (<=> = 코사인 유사도 연산자)
@Query(value = "SELECT * FROM news ORDER BY embedding <=> CAST(:queryVector AS vector) LIMIT :limit", nativeQuery = true)
List<News> findSimilarNews(@Param("queryVector") List<Float> queryVector, @Param("limit") int limit);
```

---

## 아직 없는 것 — News 엔티티 스펙

아래 스펙으로 `News.java` 작성 필요:

| 필드명 | 타입 | 설명 |
|--------|------|------|
| id | Long | PK, auto increment |
| title | String | 기사 제목 |
| content | String (@Lob) | 기사 본문 전체 |
| summary | String | AI 3줄 요약 |
| originalUrl | String | UNIQUE 제약, 중복 방지 키 |
| source | String | 출처 (예: "한국경제") |
| category | String | 금융/경제/정책 등 |
| publishedAt | LocalDateTime | 기사 발행일 |
| createdAt | LocalDateTime | DB 저장 시각 |
- embedding 컬럼은 `vector(1536)` 타입이라 JPA @Column으로 매핑하지 않음 → 네이티브 쿼리로만 처리

---

## application.yml 추가 필요 내용

```yaml
ai-service:
  base-url: http://localhost:3000
  use-stub: true   # 혜린이 API 완성 후 false로 변경

spring:
  task:
    scheduling:
      pool:
        size: 3
```

---

## AI Service 내부 API 계약 (정다은 ↔ 김혜린)

### `POST /api/ai/process`

**Request:**
```json
{
  "articleUrl": "https://www.hankyung.com/article/...",
  "title": "ETF 투자, 이것만 알고 시작하세요",
  "content": "기사 본문 전체...",
  "category": "금융"
}
```

**Response:**
```json
{
  "summary": "ETF는 여러 주식을 한 바구니에 담은 펀드입니다...\n분산투자 효과가 있어...\n수수료가 일반 펀드보다 낮아...",
  "terms": [
    {
      "term": "ETF",
      "explanation": "여러 주식을 한번에 사는 펀드예요. 삼성전자, SK하이닉스 등을 한꺼번에 살 수 있어요.",
      "startIdx": 0,
      "endIdx": 3
    }
  ],
  "embedding": [0.0012, -0.0034, ...]  // 1536차원 float 배열
}
```

**중요**: stub 모드가 true면 이 API 없어도 백엔드 테스트 가능. 혜린이가 API 완성하면 `ai-service.use-stub=false` 로 전환.

---

## DB 스키마 (관련 부분)

```sql
-- news 테이블 (init.sql 기준)
CREATE TABLE news (
    id          BIGSERIAL PRIMARY KEY,
    title       TEXT NOT NULL,
    content     TEXT NOT NULL,
    summary     TEXT,
    original_url TEXT NOT NULL UNIQUE,   -- ⚠️ UNIQUE 필수 (중복 방지)
    source      VARCHAR(100),
    category    VARCHAR(100),
    published_at TIMESTAMP,
    created_at  TIMESTAMP DEFAULT NOW(),
    embedding   vector(1536)             -- pgvector 컬럼
);

-- 벡터 인덱스 (유사도 검색 성능)
CREATE INDEX idx_news_embedding ON news USING ivfflat (embedding vector_cosine_ops);
```

---

## 현재 개발 환경

- **로컬 실행**: `docker-compose up -d` (PostgreSQL + Redis)
- **스케줄 대신 수동 테스트**: `POST /api/admin/crawl/trigger` 엔드포인트 호출
- **로그 확인 포인트**:
  - `[Crawler] 한경 금융 뉴스 URL 수집 완료: N건` → N=0이면 selector 수정 필요
  - `[AiServiceClient] STUB 모드` → 정상 (혜린이 API 전까지)
  - `[Pipeline] 저장 완료: 제목` → DB 저장 성공

---

## 주의사항 / 설계 원칙

1. **selector 실패 시** `HankyungCrawlerService`의 후보 리스트에서 크롬 개발자도구로 재확인
2. **pgvector 임베딩**은 절대 JPA `@Column`으로 매핑하지 말 것 → 네이티브 쿼리만 사용
3. **News 엔티티의 embedding 필드는 선언하지 않음** → `updateEmbedding()` 네이티브 쿼리로만 저장
4. **stub 모드는 팀 병렬 개발용** → 혜린이 API 완성 전까지 true 유지
5. **`@EnableScheduling`** 이 없으면 `@Scheduled` 작동 안 함 → CrawlerConfig에 이미 있음
