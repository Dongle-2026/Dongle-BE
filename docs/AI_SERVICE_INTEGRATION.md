# AI 서비스 연동 가이드

> Spring Boot ↔ AI Service (Node.js) 연동 구조, 테스트 방법, 버그 수정 이력을 정리한 문서.

---

## 1. 전체 아키텍처

```
[한경 크롤러]
     │  CrawledArticleDto
     ▼
[NewsProcessingService]  ── @Transactional
     │  AiProcessRequest
     │  (articleUrl, title, content, category)
     ▼
[AiServiceClient]  ── WebClient (POST /api/ai/process)
     │
     ▼
[AI Service :3000]  ── Node.js / Express
     │  ┌─ summarizer.service.js  → Ollama qwen3:8b
     │  ├─ ner.service.js         → Ollama qwen3:8b
     │  └─ embedding.service.js   → Ollama nomic-embed-text
     │
     │  AiProcessResponse
     │  (summary, terms[], embedding[768])
     ▼
[NewsProcessingService]
     │  ┌─ news_articles        (기사 본문)
     │  ├─ news_summaries       (AI 요약)
     │  ├─ financial_terms      (용어 upsert)
     │  ├─ article_term_highlights (기사↔용어 연결)
     │  └─ news_articles.embedding (768차원 벡터)
     ▼
[PostgreSQL + pgvector]
```

---

## 2. 요청/응답 스펙

### Request: `POST /api/ai/process`

| 필드 | 타입 | 설명 |
|------|------|------|
| `articleUrl` | String | 기사 원문 URL (AI에서 무시됨, 로깅용) |
| `title` | String | 기사 제목 |
| `content` | String | 기사 본문 전체 |
| `category` | String | 뉴스 카테고리 (요약 프롬프트에 사용) |

### Response

| 필드 | 타입 | 설명 |
|------|------|------|
| `summary` | String | 3줄 요약 (`1. ...\n2. ...\n3. ...` 형식) |
| `terms` | `TermDto[]` | 추출된 금융 용어 목록 (최대 7개) |
| `embedding` | `float[768]` | nomic-embed-text 임베딩 벡터 |

**TermDto 구조:**
```json
{
  "term": "ETF",
  "explanation": "주식처럼 사고팔 수 있는 펀드예요.",
  "startIdx": 0,
  "endIdx": 3
}
```

---

## 3. 설정

### application.yml (local 프로필)

```yaml
ai-service:
  base-url: http://localhost:3000
  use-stub: false          # true: stub 모드, false: 실제 AI 서비스 호출
  model-version: qwen3:8b  # news_summaries.model_version에 기록
```

### AI Service 환경변수 (`.env`)

```
PORT=3000
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b
OLLAMA_EMBED_MODEL=nomic-embed-text
REDIS_HOST=localhost
REDIS_PORT=6379
CACHE_TTL_SECONDS=86400
```

### 필요한 Ollama 모델

```bash
ollama pull qwen3:8b         # 요약 + NER
ollama pull nomic-embed-text # 임베딩 (768차원)
```

---

## 4. 로컬 실행 순서

```bash
# 1. DB + Redis
docker start youthletter-postgres youthletter-redis

# 2. AI Service
cd ~/Desktop/youthletter-ai-service/src
node server.js &

# 3. Spring Boot
cd ~/Desktop/youthletter-backend
./gradlew bootRun --args='--spring.profiles.active=local'

# 4. 크롤 수동 트리거 (Admin 권한 필요)
curl -X POST http://localhost:8080/api/admin/crawl/trigger \
  -H "Authorization: Bearer {ADMIN_JWT}"
```

---

## 5. stub 모드

stub 모드는 AI 서비스 없이 크롤링 파이프라인 전체 흐름을 테스트할 때 사용한다.

| 설정 | 동작 |
|------|------|
| `use-stub: true` | 고정 텍스트 요약, 빈 terms, 768개 0.0f 벡터 반환 |
| `use-stub: false` | AI Service 실제 호출 |

stub 전환은 `application.yml` 또는 Admin API로 변경 가능:

```bash
curl -X POST http://localhost:8080/api/admin/crawl/ai-stub \
  -H "Authorization: Bearer {ADMIN_JWT}" \
  -d '{"stubMode": true}'
```

---

## 6. DB 저장 구조

```
news_articles (기사 저장)
├── id, source_url, title, content, category, published_at, embedding(vector 768)
│
news_summaries (AI 요약)
├── article_id → news_articles.id
├── summary_text (3줄 요약)
├── model_version (예: "qwen3:8b")
└── valid_from / valid_to (버전 관리, valid_to=NULL이 현재 유효본)

financial_terms (금융 용어 사전)
├── name (UNIQUE), path (ltree), definition, simple_definition
└── AI가 새 용어 추출 시 upsert

article_term_highlights (기사↔용어 연결)
├── article_id → news_articles.id
├── term_id    → financial_terms.id
└── position_start / position_end (본문 내 위치)
```

---

## 7. 수정 이력

### 2026-05-31 — AI 연동 버그 수정

#### 버그 1: 임베딩 차원 불일치 (1536 → 768)
- **문제:** `AiServiceClient.createStubResponse()`가 1536차원 stub 벡터 생성.  
  `news` 테이블 DDL도 `vector(1536)`. 하지만 nomic-embed-text는 **768차원** 반환.
- **수정 파일:**
  - `AiServiceClient.java`: `nCopies(1536, 0.0f)` → `nCopies(768, 0.0f)`
  - `V12__fix_embedding_dimension.sql`: `news.embedding` 컬럼 1536→768 변경
  - `V13__add_embedding_to_news_articles.sql`: `news_articles.embedding vector(768)` 추가

#### 버그 2: modelVersion 하드코딩
- **문제:** `NewsProcessingService`에서 `modelVersion("stub")`으로 고정되어 있어 실제 AI 호출 시에도 "stub"으로 기록됨.
- **수정:** `@Value("${ai-service.model-version:qwen3:8b}")` 주입으로 교체. `application.yml`에 `model-version` 프로퍼티 추가.

#### 버그 3: NER 결과 미저장
- **문제:** `aiResponse.terms()` 데이터가 완전히 무시됨. `article_term_highlights` 테이블에 아무것도 저장되지 않음.
- **수정:** `NewsProcessingService.saveTermHighlights()` 메서드 추가.  
  - `financial_terms` 테이블에 용어 upsert (`findByName` 또는 신규 생성)
  - `article_term_highlights`에 기사-용어 위치 정보 연결

#### 버그 4: 임베딩 미저장
- **문제:** `aiResponse.embedding()` 데이터가 저장되지 않음. 벡터 유사도 검색 불가.
- **수정:**
  - `NewsArticleRepository.updateEmbedding()` native query 추가
  - `NewsProcessingService.saveEmbedding()` 메서드 추가

---

## 8. 알려진 한계

| 항목 | 설명 |
|------|------|
| 순차 처리 | 기사 1건당 AI 처리 ~2.5분 (Ollama 로컬 추론). 기사 10건 → 약 25분 소요 |
| 단일 트랜잭션 | `processAll()` 전체가 `@Transactional`. 마지막 기사 실패 시 전체 롤백 위험 |
| FinancialTerm path | AI 추출 용어의 ltree path가 `"finance"`로 고정됨. 추후 카테고리 분류 로직 필요 |
| 중복 요약 버전 관리 | 현재 `summaryVersion`이 항상 1로 고정. 재크롤 시 이전 요약 만료 처리 없음 |
