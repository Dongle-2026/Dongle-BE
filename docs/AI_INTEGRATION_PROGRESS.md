# AI 서비스 연동 작업 정리

> 작업일: 2026-05-29 ~ 2026-06-01  
> 브랜치: `feature/card-news`  
> 작업자: 정다은

---

## 작업 개요

Spring Boot 백엔드와 Node.js AI 서비스를 실제로 연동하고, stub 모드 없이 동작하는 AI 파이프라인을 완성했다.

**전체 흐름:**
```
한경 크롤러 → Spring Boot → AI Service (Node.js/Ollama) → PostgreSQL
```

AI Service가 하는 일:
- **요약** — `qwen3:8b` 모델로 기사를 3줄 한국어 요약
- **NER** — `qwen3:8b`로 금융 용어 최대 7개 추출 (위치 정보 포함)
- **임베딩** — `nomic-embed-text`로 768차원 벡터 생성

---

## 1. 스펙 검증

### Spring Boot ↔ AI Service 요청/응답 비교

연동 전, 양쪽 코드를 직접 대조해서 스펙이 맞는지 확인했다.

**요청 (`AiProcessRequest`)**

| Spring Boot 필드 | AI Service 사용 여부 |
|-----------------|---------------------|
| `articleUrl` | ❌ 사용 안 함 (무시됨) |
| `title` | ✅ 요약 프롬프트에 사용 |
| `content` | ✅ 요약/NER/임베딩 모두 사용 |
| `category` | ✅ 요약 프롬프트에 사용 |

**응답 (`AiProcessResponse`)**

| 필드 | Spring Boot 타입 | AI Service 반환 타입 | 결과 |
|------|----------------|-------------------|------|
| `summary` | `String` | `String` | ✅ 일치 |
| `terms[].term` | `String` | `String` | ✅ 일치 |
| `terms[].explanation` | `String` | `String` | ✅ 일치 |
| `terms[].startIdx` | `int` | `int` | ✅ 일치 |
| `terms[].endIdx` | `int` | `int` | ✅ 일치 |
| `embedding` | `List<Float>` | `float[]` (768차원) | ✅ 일치 |

---

## 2. 발견된 버그 및 수정 내용

스펙 비교와 실제 연동 테스트 과정에서 총 4개의 버그를 발견했다.

---

### 버그 1 — 임베딩 차원 불일치 (1536 vs 768)

**문제**

`AiServiceClient.java`의 stub 응답이 1536차원 벡터를 만들고 있었고, `news` 테이블 DDL도 `vector(1536)`으로 정의되어 있었다. 하지만 실제 AI Service가 사용하는 `nomic-embed-text` 모델은 **768차원**을 반환한다. stub 모드를 끄면 DB 저장 시 오류가 나는 상황.

**수정**

- `AiServiceClient.java`: `Collections.nCopies(1536, 0.0f)` → `nCopies(768, 0.0f)`
- `V12__fix_embedding_dimension.sql` 추가: `news.embedding` 컬럼 `vector(1536)` → `vector(768)` 변경

---

### 버그 2 — `modelVersion` 하드코딩

**문제**

`NewsProcessingService.java`에서 `NewsSummary`를 저장할 때 `modelVersion("stub")`으로 고정되어 있었다. 실제 AI 서비스를 호출해도 DB에는 항상 `model_version = 'stub'`으로 기록되는 문제.

**수정**

- `application.yml`에 `ai-service.model-version: qwen3:8b` 프로퍼티 추가
- `NewsProcessingService`에 `@Value("${ai-service.model-version:qwen3:8b}") String modelVersion` 주입
- `modelVersion("stub")` → `modelVersion(modelVersion)` 으로 교체

---

### 버그 3 — NER 결과 미저장

**문제**

AI Service가 반환하는 `terms` (금융 용어 + 본문 위치 정보)가 완전히 무시되고 있었다. `article_term_highlights` 테이블에 아무것도 저장되지 않아 용어 하이라이팅, 용어별 관련 뉴스 기능이 동작 불가능한 상태.

**수정**

`NewsProcessingService`에 `saveTermHighlights()` 메서드 추가:
1. AI가 추출한 용어명으로 `financial_terms` 테이블 조회
2. 없으면 새 용어로 upsert (path: `"finance"`, 설명: AI가 생성한 explanation 사용)
3. `article_term_highlights`에 기사-용어 연결 + 본문 위치(`startIdx`, `endIdx`) 저장

---

### 버그 4 — 임베딩 미저장

**문제**

AI Service가 반환하는 `embedding` 벡터가 저장되지 않고 있었다. 두 가지 원인:
1. `news_articles` 테이블에 `embedding` 컬럼 자체가 없었음
2. 서비스 코드에 저장 로직이 없었음

pgvector 벡터 유사도 검색(RAG, 유사 뉴스 추천) 기능이 동작 불가능한 상태.

**수정**

- `V13__add_embedding_to_news_articles.sql` 추가: `news_articles.embedding vector(768)` 컬럼 + HNSW 인덱스 생성
- DB에 직접 `ALTER TABLE news_articles ADD COLUMN embedding vector(768)` 실행
- `NewsArticleRepository`에 `updateEmbedding()` native query 추가
- `NewsProcessingService`에 `saveEmbedding()` 메서드 추가

---

## 3. 실제 연동 테스트 결과

### 환경 구성

| 서비스 | 포트 | 상태 |
|--------|------|------|
| PostgreSQL | 15432 | Docker |
| Redis | 6379 | Docker |
| AI Service (Node.js) | 3000 | 로컬 직접 실행 |
| Spring Boot | 8080 | `./gradlew bootRun` |
| Ollama | 11434 | 로컬 (자동 실행 중) |

**사용 모델**
- 요약/NER: `qwen3:8b`
- 임베딩: `nomic-embed-text`

### AI Service 직접 호출 테스트

```
POST http://localhost:3000/api/ai/process
```

응답 결과 확인:
- `summary`: 3줄 한국어 요약 ✅
- `terms`: 금융 용어 7개 추출 + 본문 위치 정보 ✅
- `embedding`: 768차원 float 벡터 ✅

### 전체 파이프라인 테스트

`POST /api/admin/crawl/trigger` 실행 결과:

```
[Pipeline] 한경 금융 뉴스 URL 수집 완료: 38건
[Pipeline] 저장 완료: 경제 '반도체 호황'에 올해 명목성장률 24년만에 10%대 가능성
...
[Pipeline] 파이프라인 완료 — 총 37건 저장, 0건 실패
```

### DB 저장 결과

| 항목 | 결과 |
|------|------|
| 신규 저장 기사 | 37건 |
| 실제 AI 요약 저장 | 37건 |
| terms/embedding | 0건 (파이프라인이 버그 수정 전에 돌았음) |

> terms, embedding은 다음 크롤 실행부터 수정된 코드가 적용되어 저장된다.

---

## 4. 변경된 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `AiServiceClient.java` | stub 임베딩 차원 1536 → 768 |
| `NewsProcessingService.java` | `saveTermHighlights()`, `saveEmbedding()` 추가, `modelVersion` 프로퍼티 주입 |
| `NewsArticleRepository.java` | `updateEmbedding()` native query 추가 |
| `application.yml` | `ai-service.model-version: qwen3:8b` 추가 |
| `V12__fix_embedding_dimension.sql` | `news.embedding` 1536→768 |
| `V13__add_embedding_to_news_articles.sql` | `news_articles.embedding vector(768)` + HNSW 인덱스 |
| `docs/AI_SERVICE_INTEGRATION.md` | AI 연동 구조/스펙/설정 가이드 |

**커밋**
```
08eea3d  fix: 임베딩 차원 1536 → 768로 수정 (nomic-embed-text 기준)
19b1f23  fix: AI 연동 파이프라인 버그 수정 (modelVersion/terms/embedding 저장)
```

---

## 5. 로컬 실행 순서

```bash
# 1. DB + Redis 시작
docker start youthletter-postgres youthletter-redis

# 2. AI Service 시작
cd ~/Desktop/youthletter-ai-service/src
node server.js &

# 3. Spring Boot 시작
cd ~/Desktop/youthletter-backend
./gradlew bootRun --args='--spring.profiles.active=local'

# 4. 테스트 콘솔
# http://localhost:8080/test.html
# → 로그인 → 8. Admin → 크롤링 실행
# → 3. News → 뉴스 상세에서 summary 필드 확인
```

---

## 6. 남은 알려진 한계

| 항목 | 내용 |
|------|------|
| 순차 처리 | 기사 1건당 AI 처리 약 2~3분 (Ollama 로컬 추론 속도 한계). 기사 10건 기준 약 20~30분 소요 |
| 단일 트랜잭션 | `processAll()`이 `@Transactional`로 묶여 있어 마지막 기사에서 예외 발생 시 전체 롤백 가능성 있음 |
| FinancialTerm path | AI가 추출한 용어의 `path`가 모두 `"finance"`로 고정됨. 추후 카테고리 분류 로직 필요 |
| 요약 버전 관리 | 재크롤 시 이전 요약이 만료 처리되지 않아 동일 기사에 유효한 요약이 중복 생성될 수 있음 |
