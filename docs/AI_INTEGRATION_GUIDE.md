# 돈글돈글 백엔드·AI 연결 실무 가이드

기준: 2026-09-28. 먼저 `IMPLEMENTATION_CHECKLIST.md`, 다음 이 문서, 마지막 `DEVELOPMENT_DECISIONS.md`를 읽는다. 기존 제안서의 과거 성과와 이번 실행 결과는 구별한다.

## 1. 연결 구조와 각 서버의 책임

앱(React Native/TypeScript) → Spring Boot(8080) → Node AI Service(3000) → Ollama(11434).

앱은 Spring만 호출한다. 로그인 검증과 사용자의 데이터 조회는 Spring이 수행한다. Node는 전달받은 기사 또는 익명 소비 집계로 프롬프트를 실행한다. Ollama는 모델 실행기이며 그 자체가 우리 서비스의 인증·뉴스·소비 API는 아니다. PostgreSQL과 Redis는 Spring이 관리한다. Node가 DB를 직접 수정하지 않으므로 모델 출력 검증과 DB 저장을 각각 확인할 수 있다.

이번에 추가한 `ai-service/`는 기존 별도 저장소를 찾지 못한 상황에서도 실행 가능한 기준 구현이다. 김혜린 담당자의 서비스가 별도로 있다면 아래 HTTP 계약에 맞추어 `AI_SERVICE_BASE_URL`만 바꾸거나 이 구현을 해당 저장소로 옮긴다. 같은 포트에서 두 서버를 동시에 실행하지 않는다.

## 2. 준비할 것

| 항목 | 누가/어떻게 | 필요 시점 |
|---|---|---|
| Java 17, Node 22 이상 | 설치 확인 (`java -version`, `node --version`) | 백엔드/Node 실행 |
| Docker Desktop | 실행 후 `docker info` | PostgreSQL·Redis |
| Ollama | 설치 후 실행 | 실제 모델 추론 |
| qwen3:8b | `ollama pull qwen3:8b` | 요약·NER·카드·소비 제안 |
| nomic-embed-text | `ollama pull nomic-embed-text` | 768차원 임베딩 |
| AI 서비스 공유 토큰 | 운영 환경에서 팀이 비밀값 생성, 양 서버에 동일 설정 | 로컬 외 접근 |
| Firebase 프로젝트·앱 ID·서비스 계정 | 다은님/앱 담당자가 소유한 계정에서 발급 | 후속 실제 푸시 발송 |
| Expo Development Build | 앱 담당자가 네이티브 설정 포함해 빌드 | 실제 푸시·모바일 OAuth |
| OAuth 앱 callback URI | 앱 담당자와 scheme 확정 후 provider 콘솔 등록 | 모바일 소셜 로그인 |

현재 Ollama 모델은 이미 로컬에 설치되어 있음을 확인했다. 다시 다운로드할 필요 없이 `ollama list`로 확인한다. OpenAI API 키나 유료 모델 결제는 이번 구현에 필요하지 않다.

## 3. 실행 순서

### A. 데이터베이스

기존 개발 DB가 있다면 백업 후 아래 **추가 DDL**을 검토한다. `init.sql`은 빈 DB용이며 기존 DB에 재실행하지 않는다. 현재 Gradle에 Flyway가 없으므로 `db/migration` 파일은 자동 실행되지 않는다.

```bash
# 이번에 작성한 비파괴 추가 DDL: 기존 테이블/데이터 삭제 없음
psql -h localhost -p 15432 -U youthletter -d youthletter \
  -v ON_ERROR_STOP=1 -f docker/postgres/upgrade-capstone.sql
```

암호는 프롬프트 또는 개인 환경변수로 전달한다. SQL은 뉴스 임베딩, 카드뉴스/카드, 생성 메타데이터, 기기 토큰 테이블을 추가한다. 이미 다른 차원으로 생성된 임베딩 컬럼의 차원 변환은 수행하지 않는다. `\d news_articles`에서 `vector(768)`인지 확인한다.

검증용 DB는 개발 DB와 별도 포트다.

```bash
docker compose -f docker-compose.test.yml up -d
./gradlew test --no-daemon
```

새 테스트 DB는 init.sql과 upgrade-capstone.sql을 순서대로 실행한다. 기존 테스트 DB 볼륨이 있으면 추가 SQL을 테스트 DB(5433, youthletter_test)에 별도로 적용해야 한다. 개발 DB(15432)를 테스트 실행 대상으로 설정하지 않는다.

### B. Ollama

```bash
ollama list
# 앱이 이미 실행 중이면 별도 serve 불필요
ollama serve
curl http://127.0.0.1:11434/api/tags
```

`address already in use`는 이미 서버가 실행 중일 수 있다는 뜻이다. tags 응답으로 확인한다. 실제 모델 파일을 다운로드하거나 설치하더라도 모델을 실행하는 서버가 켜져 있어야 한다.

### C. Node AI Service

```bash
cd ai-service
npm ci
npm test
npm start
```

기본 주소는 `127.0.0.1:3000`. `.env.example` 내용을 필요에 맞게 `.env`에 복사한다. Node 22 이상의 `--env-file-if-exists`로 읽는다. `.env`는 Git에 포함하지 않는다.

| 환경변수 | 기본 | 의미 |
|---|---|---|
| HOST / PORT | 127.0.0.1 / 3000 | 서버 바인딩 주소 |
| OLLAMA_BASE_URL | http://127.0.0.1:11434 | 모델 실행 서버 |
| OLLAMA_CHAT_MODEL | qwen3:8b | 생성 모델 |
| OLLAMA_EMBED_MODEL | nomic-embed-text | 임베딩 모델 |
| OLLAMA_TIMEOUT_MS | 120000 | 모델 호출 1회 제한 |
| AI_SERVICE_TOKEN | 빈 값 | 로컬 외 바인딩 시 필수 |

동시에 한 생성 요청만 처리한다. 처리 중 다른 요청은 429 `AI_BUSY`를 반환한다. 이것은 영속 작업 큐가 아니다. 소규모 개발 장비에서 추론이 겹쳐 메모리가 부족해지는 문제를 막는 초기 구현이다.

### D. Spring Boot

프로젝트 루트의 터미널에서 다음 환경변수를 설정하고 실행한다.

```bash
export AI_SERVICE_BASE_URL=http://127.0.0.1:3000
export AI_USE_STUB=false
export AI_MODEL_VERSION=qwen3:8b
./gradlew bootRun --args='--spring.profiles.active=local'
```

AI_SERVICE_TOKEN을 사용하는 경우 Spring과 Node에 같은 값을 넣는다. Spring이 `.env`를 자동 로딩한다고 가정하지 않는다. IntelliJ Run Configuration의 Environment variables 또는 터미널 export를 사용한다. JWT/DB/OAuth 환경변수는 기존 설정을 따른다.

`http://localhost:8080/swagger-ui.html`에서 앱 API를 확인한다. 실제 기기에서 localhost는 휴대폰 자신을 의미한다. 앱 API baseURL은 개발 Mac의 LAN 주소:8080으로 설정하고 동일 네트워크에서 연결한다. 앱이 3000/11434 포트에 직접 접근하게 만들지 않는다.

## 4. 테스트 계정과 관리자 권한

회원가입은 일반 사용자로 생성한다. `/api/admin/**`와 카드 생성 API는 관리자 권한이 필요하다. 운영 회원을 임의로 승격하지 말고 테스트 DB 전용 계정을 사용한다. 기존 관리자 계정을 사용할 때도 토큰을 문서·Git에 저장하지 않는다.

JWT 역할이 바뀌면 기존 Access Token에 반영되지 않을 수 있으므로 다시 로그인한다. 일반 사용자도 공개 카드 목록과 상세는 조회할 수 있다.

## 5. 앱에서 호출할 API

기존 wrapper는 `{ "status": 200, "message": "ok", "data": ... }`다. 초기 AGENTS 예시의 `success` wrapper와 다르지만 기존 앱 호환성을 위해 변경하지 않았다. 빈 data는 생략될 수 있다.

| 메서드 / 경로 | 입력/용도 | 인증 |
|---|---|---|
| POST /api/auth/signup | 기존 SignupRequest | 공개 |
| POST /api/auth/login | email, password | 공개 |
| POST /api/auth/reissue | refreshToken | 토큰 검증 |
| POST /api/auth/logout | 기존 Authorization 헤더 | 사용자 |
| DELETE /api/auth/withdraw | 회원 탈퇴 | 사용자 |
| GET, PATCH /api/users/me | 프로필 조회/수정 | 사용자 |
| PUT /api/users/me/categories | 관심분야 | 사용자 |
| PUT /api/users/me/policy-profile | 추천 조건 | 사용자 |
| GET /api/users/me/learning-stats | 학습 현황 | 사용자 |
| GET /api/users/me/bookmarks | 저장 뉴스, page/size | 사용자 |
| GET /api/users/me/policy-bookmarks | 저장 정책 | 사용자 |
| GET /api/users/me/learned-terms | 학습 용어 | 사용자 |
| GET /api/news | category, **keyword**, **date=YYYY-MM-DD**, page=0, size=20 | 공개 |
| GET /api/news/today | 오늘 수집 기사 | 공개/개인화 선택 |
| GET /api/news/recommend | 최대 3개, reasonCode | **사용자 필수** |
| GET /api/news/{articleId} | 원문·요약·용어 하이라이트 | 공개 |
| POST /api/news/{articleId}/read | durationSeconds 선택 | 사용자 |
| POST /api/news/{articleId}/bookmark | 토글, bookmarked 반환 | 사용자 |
| GET /api/card-news | 게시 목록 page=0, size=20 | 공개 |
| GET /api/card-news/{cardNewsId} | 카드뉴스 자체 ID | 공개 |
| GET /api/card-news/article/{articleId} | 기사 ID로 카드 조회 | 공개 |
| POST /api/card-news/{articleId}/generate | 실제 생성 후 저장, 동기 | 관리자 |
| GET /api/terms 및 기존 /search, /autocomplete, /category, /{id} | 금융 사전 | 공개 |
| POST /api/terms/{termId}/learn | 학습 기록 | 사용자 |
| GET /api/policies | 기존 목록 조건 | 공개 |
| GET /api/policies/{policyId} | 정책 상세 | 공개 |
| GET /api/policies/recommend | 프로필 기반 후보 | 사용자 |
| POST /api/policies/{policyId}/bookmark | 북마크 토글 | 사용자 |
| POST /api/spending | 소비 생성 | 사용자 |
| PUT /api/spending/{spendingId} | 기존 전체 수정 DTO | 사용자 |
| DELETE /api/spending/{spendingId} | 삭제 이벤트 | 사용자 |
| GET /api/spending?year=2026&month=9 | 현재 유효한 월 소비 | 사용자 |
| GET /api/spending/dashboard?month=2026-09 | 실제 이벤트 집계 | 사용자 |
| GET /api/spending/trend | 당월 포함 6개월, 빈 월 0 | 사용자 |
| POST /api/spending/budget | monthlyBudget | 사용자 |
| GET /api/spending/budget/status | 예산 사용률 | 사용자 |
| POST /api/spending/ai-insight | 당월·전월 집계 + 제안 | 사용자 |
| GET /api/notifications | 기존 DB 알림 | 사용자 |
| PATCH /api/notifications/{id}/read 및 /read-all | 읽음 | 사용자 |
| PUT /api/users/me/devices | deviceId, token, platform | 사용자 |
| DELETE /api/users/me/devices/{deviceId} | 해당 계정 기기 해제 | 사용자 |
| GET /api/admin/ai/health | Node와 설치 모델 상태 | 관리자 |
| POST /api/admin/ai/articles/{articleId}/process | 기존 기사 요약·NER·벡터 재처리 | 관리자 |
| POST /api/admin/crawl/trigger | 전체 크롤·처리 | 관리자 |
| GET /api/admin/crawl/status | 마지막 크롤 상태 | 관리자 |
| GET /api/health | 기존 DB·Redis 진단 별칭 | 공개 |

검색 API에는 제목 또는 본문 키워드 조건을 적용한다. `%`, `_`는 현재 LIKE 와일드카드로 해석된다. UI에서 검색 조건이 바뀌면 page를 0으로 초기화한다. 서버 최대 size는 100이다.

### 카드 ID 혼동 방지

`GET /api/card-news/5`의 5는 카드뉴스 ID이다. `GET /api/card-news/article/5`의 5는 기사 ID이다. 생성 경로의 ID는 기사 ID다. DTO는 기존 `cards[].cardOrder`, `title`, `body`, `templateType`를 유지하며 `sourceSentenceIds`, 상위 `modelVersion`, `promptVersion`을 추가했다.

### 기기 등록 예시

```json
{"deviceId":"앱이-보관하는-기기-식별자","token":"FCM에서-받은-실제-토큰","platform":"ANDROID"}
```

platform은 `IOS` 또는 `ANDROID`. 등록 API는 FCM 발송 기능이 아니다. 현재는 토큰 저장/갱신/해제 기반만 제공한다. 로그아웃 전에 자신의 기기 해제를 호출한다. 다중 계정 기기 이전과 서버 logout 일괄 해제는 후속 발송 구현 전에 보완해야 한다. 실제 알림 발송은 아직 활성화하지 않았다.

## 6. 내부 AI HTTP 계약

Node 응답은 Spring wrapper 없이 JSON이다. 입력 기사 최대 16,000자, 제목 최대 300자. 초과 기사는 명시적 실패 처리한다. 긴 기사 자동 chunking은 후속 항목이며 자르면서 원문 위치를 틀리게 만들지 않는다.

### POST /api/ai/process

```json
{"articleUrl":"https://example.invalid/article","title":"기사 제목","content":"기사 전체 본문","category":"YOUTH"}
```

출력: `summary` 문자열, `terms:[{term,explanation,startIdx,endIdx}]`, `embedding:[768개 float]`.

NER 위치는 모델이 만든 숫자를 믿지 않고 원문 문자열에서 계산한다. 원문에 없는 후보는 제외한다. 위치는 JS/Java/TypeScript 공통 UTF-16 인덱스이고 endIdx는 exclusive다. 기사 HTML 정제 후 전송한 본문을 그대로 저장해야 하이라이트가 맞는다.

### POST /api/ai/card-news

같은 기사 입력. 출력: `modelVersion`, `promptVersion`, `cards`(5~7개).

문장을 줄바꿈 또는 종결부호 뒤 공백으로 분리해 1부터 번호를 붙인다. 모델은 이 번호를 근거로 반환한다. Node와 Spring 모두 카드 순서/타입/필수 값/길이/근거 번호/숫자를 검사한다. 수치 검증은 문자열 기반 보조 검증이며 인과관계와 의미까지 사실임을 보장하지 않는다. 고정 기사 평가와 사람 검수가 필요하다. JSON 모드 + 수동 검증을 사용한다. 설치된 Ollama에서 JSON Schema 직접 전달 시 grammar 오류가 재현되었기 때문이다.

생성 실패는 503 계열로 전달되어 기존 게시물을 보존한다. UI는 카드 404/생성 실패 시 기존 뉴스 상세와 원문 링크를 표시하면 된다. 실제 AI 호출 실패를 스텁으로 대체하지 않는다. `AI_USE_STUB=true`(Ollama 없이 개발)일 때는 Node를 호출하지 않고 정적 스텁 카드 7장을 같은 저장 흐름으로 저장하며 modelVersion/promptVersion이 `stub`으로 표시된다. 스텁 저장은 `local` 또는 `test`에서만 허용하며 `prod`가 함께 활성화되면 403으로 차단한다. 기존 실제 카드(모델 정보 없는 기존 카드 포함)를 스텁으로 덮어쓰려 하면 409로 거부한다. 개발용 스텁은 목록에 노출되므로 앱에서 `modelVersion=stub`을 보고 개발 데이터로 표시해야 한다. 생성은 관리자 동기 요청이다. 앱 홈 진입마다 호출하지 말고 관리자 사전 생성 후 목록·상세를 조회한다.

### POST /api/ai/spending-insight

```json
{"month":"2026-09","total":10000,"previousTotal":8000,"categoryTotals":{"FOOD":10000}}
```

출력: `advice`, `modelVersion`. 숫자는 Spring이 실제 BigDecimal 집계로 문장화한다. 모델에는 숫자 없는 점검 제안만 요구한다. 숫자가 포함된 제안은 거부하며 장애/데이터 없음에는 `modelVersion=rule-based-v1`로 실제 수치와 기본 문장을 반환한다. 사용자 ID, 이메일, 메모는 전송하지 않는다. 한글로 쓴 수량이나 부적절한 판단까지 자동 검증하는 단계는 아니므로 사람 평가가 필요하다.

## 7. 첫 연동 확인 순서

1. `curl http://127.0.0.1:11434/api/tags`로 두 모델 확인.
2. `curl http://127.0.0.1:3000/health`로 Node→Ollama 연결 확인.
3. ai-service 디렉터리에서 `node scripts/smoke.mjs` 실행. 가상 기사·소비 집계만 사용한다.
4. Spring에서 관리자 로그인 후 `/api/admin/ai/health` 호출.
5. DB의 기존 기사 ID에 `/api/admin/ai/articles/{id}/process` 호출.
6. `/api/news/{id}`에서 summary/modelVersion/termHighlights 확인.
7. `/api/card-news/{id}/generate` 호출 후 `/api/card-news/article/{id}` 확인.
8. 앱 Mock Data 공급 부분을 위 상세 API로 교체.
9. 본인 소비 등록 후 `/api/spending/ai-insight` 숫자를 dashboard와 대조.
10. Node를 종료한 뒤 소비 응답이 rule-based-v1인지, 기존 카드는 여전히 조회되는지 확인.

## 8. 오류 진단

| 증상 | 확인 |
|---|---|
| connection refused | 해당 서버 미실행, localhost 포트 확인 |
| /health 503 MISSING_MODELS | 모델 이름/태그 확인 후 pull |
| OLLAMA_ERROR | 모델 로딩 실패·컨텍스트 초과, Ollama 로그 확인 |
| INVALID_EMBEDDING | 768차원이 아니거나 0/NaN 벡터 |
| AI_BUSY 429 | 앞 작업 종료 후 재시도, 앱 중복 클릭 방지 |
| 401/403 | JWT/관리자 역할/공유 토큰 확인 |
| 카드 503 | 길이·근거·수치 검증 거부 또는 모델 장애 |
| 카드 404 | 기사와 카드 ID 혼동 또는 아직 미생성 |
| DB missing table/column | 추가 SQL 미적용, Flyway 자동 실행 아님 |
| Gradle DB 연결 실패 | 테스트 DB 5433, Redis 6380 확인 |
| Git 실행 Xcode license 오류 | 다은님이 Xcode 실행 후 사용권 계약 직접 검토·동의 |

## 9. 검증 명령

```bash
# 프로젝트 루트 (실제 Postgres 테스트 DB 필요)
./gradlew test --no-daemon
# ai-service 디렉터리 (모델/네트워크 없이 단위 검증)
npm test
# ai-service 디렉터리 (실제 모델 추론)
node scripts/smoke.mjs
```

단위 테스트 통과와 실제 금융 기사 품질 검증은 다르다. `VERIFICATION_RESULTS.md`에 이번 실행 결과와 미검증 범위를 남긴다.

## 공식 API 근거

- [Ollama Chat API](https://docs.ollama.com/api/chat): stream=false, format, think 설정.
- [Ollama Embed API](https://docs.ollama.com/api/embed): embeddings 배열과 truncate=false 사용.
- [Structured outputs](https://docs.ollama.com/capabilities/structured-outputs): 구조화 출력과 별도 검증 참고.
