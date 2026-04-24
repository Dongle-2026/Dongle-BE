# 유스레터 Backend (Spring Boot)

청년 금융 AI 플랫폼 — Youth Letter 메인 API 서버

## 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.4.4 |
| Database | PostgreSQL + pgvector | 16 |
| Cache | Redis | 7 |
| ORM | Spring Data JPA + QueryDSL | 5.1.0 |
| Auth | Spring Security + JWT + OAuth2 | — |
| API Docs | springdoc-openapi (Swagger) | 2.8.5 |
| Crawling | Jsoup | 1.18.3 |
| Build | Gradle | 8.12 |
| Container | Docker Compose v2 | — |

---

## 처음 시작할 때 (초기 세팅)

### 사전 준비 (1회만)

| 도구 | 버전 | 확인 명령 |
|------|------|----------|
| Java | 17 | `java -version` |
| Docker Desktop | 최신 | Applications에서 Docker 실행 |
| IntelliJ IDEA | 최신 | — |

> **주의:** `docker-compose` (하이픈)는 구버전. 항상 `docker compose` (공백)를 사용할 것.

---

### Step 1. 저장소 클론

```bash
git clone <repo-url>
cd youthletter-backend
```

---

### Step 2. Docker Desktop 실행

Mac Spotlight (`Cmd + Space`) → `Docker` 검색 → 실행  
메뉴바에 고래 아이콘이 생기고 "Docker Desktop is running"이 뜰 때까지 대기 (약 30초~1분)

---

### Step 3. 개발용 인프라 시작 (PostgreSQL + Redis)

```bash
docker compose -f docker-compose.dev.yml up -d
```

이 명령 하나로:
- PostgreSQL 16 (pgvector/ltree/pg_trgm 포함) → `localhost:5432`
- Redis 7 → `localhost:6379`
- `docker/postgres/init.sql` 자동 실행 → 전체 테이블 + 인덱스 + 시드 데이터 생성

컨테이너 상태 확인:
```bash
docker compose -f docker-compose.dev.yml ps
```

---

### Step 4. 환경변수 설정

```bash
cp .env.example .env
```

`.env` 파일을 열어서 OAuth2 키만 실제 값으로 교체 (나머지는 기본값으로 동작):

```
KAKAO_CLIENT_ID=실제_카카오_REST_API_키
KAKAO_CLIENT_SECRET=실제_카카오_클라이언트_시크릿
GOOGLE_CLIENT_ID=실제_구글_클라이언트_ID
GOOGLE_CLIENT_SECRET=실제_구글_클라이언트_시크릿
```

> OAuth2 없이 JWT 로그인만 테스트하려면 `.env` 없이 그냥 실행해도 됩니다.

---

### Step 5. IntelliJ에서 프로젝트 열기

1. IntelliJ → **File → Open** → `youthletter-backend` 폴더 선택
2. "Open as **Gradle Project**" 선택 (중요)
3. 우하단에 Gradle sync 진행 표시 → 완료까지 대기
4. 우측 **Gradle 패널 → 새로고침 버튼** 클릭해서 sync 확인

> Gradle sync가 안 되면: **File → Sync Project with Gradle Files**

---

### Step 6. 애플리케이션 실행

`src/main/java/com/youthletter/YouthLetterApplication.java` 파일 열기  
→ 왼쪽 ▶️ 버튼 클릭 → **Run 'YouthLetterApplication'**

또는 터미널:
```bash
./gradlew bootRun
```

콘솔에 아래 메시지가 나오면 정상 실행:
```
Started YouthLetterApplication in X.XXX seconds
```

---

### Step 7. 실행 확인

| 확인 항목 | URL |
|----------|-----|
| 헬스체크 | http://localhost:8080/api/health |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| API 명세 (JSON) | http://localhost:8080/v3/api-docs |

---

## 테스트 실행

### 로컬에서 테스트할 때

테스트는 별도의 `youthletter_test` DB가 필요합니다. (개발용 DB와 분리)

```bash
# 1. 테스트용 DB/Redis 시작 (5433/6380 포트 — 개발용과 충돌 없음)
docker compose -f docker-compose.test.yml up -d

# 2. 테스트 실행
./gradlew test

# 3. 테스트 후 컨테이너 정리
docker compose -f docker-compose.test.yml down
```

IntelliJ에서 실행하려면:  
**Gradle 패널 → Tasks → verification → test** 더블클릭

### CI (GitHub Actions)

PR을 올리면 자동으로 실행됩니다. `.github/workflows/backend-ci.yml` 참고.

---

## 매일 개발 시작 루틴

```bash
# 1. Docker Desktop 실행 (메뉴바 고래 아이콘 확인)

# 2. 컨테이너 시작 (이미 떠 있으면 생략)
docker compose -f docker-compose.dev.yml up -d

# 3. IntelliJ에서 Run (또는 ./gradlew bootRun)
```

---

## 자주 쓰는 명령어

```bash
# 컨테이너 상태 확인
docker compose -f docker-compose.dev.yml ps

# 컨테이너 중지
docker compose -f docker-compose.dev.yml down

# PostgreSQL 직접 접속
docker exec -it youthletter-postgres psql -U youthletter -d youthletter

# Redis 직접 접속
docker exec -it youthletter-redis redis-cli

# 컨테이너 로그 확인
docker logs -f youthletter-postgres

# 테스트만 실행 (DB 없이 빌드만 하려면)
./gradlew build -x test

# Gradle 캐시 초기화
./gradlew clean build
```

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| `Cannot connect to the Docker daemon` | Docker Desktop 꺼져 있음 | Docker Desktop 실행 |
| `contextLoads()` 테스트 실패 | 테스트 DB 없음 | `docker compose -f docker-compose.test.yml up -d` |
| `command not found: docker-compose` | 구버전 CLI | `docker compose` (공백)으로 실행 |
| Gradle sync 실패 | JDK 설정 오류 | IntelliJ → Project Structure → SDK → Java 17 설정 |
| 포트 5432 이미 사용 중 | 로컬 PostgreSQL 충돌 | `brew services stop postgresql` |

---

## 프로젝트 구조

```
src/main/java/com/youthletter/
├── YouthLetterApplication.java
├── config/                    # 설정 클래스
│   ├── JpaAuditConfig        # @CreatedDate 활성화
│   ├── SecurityConfig        # JWT + OAuth2 + CORS
│   ├── RedisConfig           # JSON 직렬화
│   ├── SwaggerConfig         # API 문서 + JWT 인증
│   └── WebClientConfig       # AI Service / 외부 API 호출
├── domain/                    # 도메인별 패키지 (DDD 구조)
│   ├── user/                  # 사용자, OAuth, 프로필
│   ├── news/                  # 뉴스 크롤링, AI 요약, 용어 하이라이트
│   ├── term/                  # 금융 용어 사전 (ltree + tsvector + pgvector)
│   ├── policy/                # 정책 추천, 즐겨찾기, 알림
│   ├── spending/              # 소비 분석 (Event Sourcing)
│   ├── guide/                 # 주식/보험 가이드
│   ├── agent/                 # AI Agent 채팅 (RAG)
│   └── activity/              # 읽기 기록, 용어 조회 로그
└── global/                    # 공통 모듈
    ├── response/GlobalResponse  # 통일 응답 래퍼
    ├── exception/               # 에러 코드 + 글로벌 핸들러
    ├── auth/                    # JWT 필터
    └── util/                    # BaseEntity, 헬스체크
```

---

## API 엔드포인트 (주요)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | `/api/health` | 헬스체크 | 불필요 |
| GET | `/api/news` | 뉴스 목록 (카테고리 필터, 페이지네이션) | 불필요 |
| GET | `/api/news/{id}` | 뉴스 상세 (원문 + 요약 + 용어 하이라이트) | 불필요 |
| GET | `/api/terms/{id}` | 용어 상세 | 불필요 |
| GET | `/api/terms/search?query=` | 용어 전문 검색 (tsvector) | 불필요 |
| GET | `/api/terms/autocomplete?query=` | 용어 자동완성 (pg_trgm) | 불필요 |
| GET | `/api/policies/recommend` | 맞춤 정책 추천 | 필요 |
| POST | `/api/policies/{id}/bookmark` | 정책 즐겨찾기 토글 | 필요 |
| POST | `/api/spending` | 소비 기록 추가 | 필요 |
| GET | `/api/spending/dashboard` | 월별 소비 대시보드 | 필요 |

전체 API는 Swagger UI에서 확인: http://localhost:8080/swagger-ui/index.html

---

## DB 설계 특이사항

1. **Temporal Versioning** (`news_summaries`): AI 요약 버전 관리. `valid_from`/`valid_to`로 SCD Type 2 구현
2. **ltree** (`financial_terms.path`): 금융 용어 계층 구조. `<@` 연산자로 O(log N) 하위 조회
3. **Hybrid Search** (`financial_terms`): tsvector(키워드 매칭) + pgvector(의미 유사도) 결합
4. **Event Sourcing** (`spending_events`): INSERT-only. CQRS 패턴으로 읽기/쓰기 분리
5. **RLS** (Row-Level Security): DB 레벨 접근 제어. 코드 버그로 다른 사용자 데이터 접근 원천 차단

---

## 팀원별 작업 영역

| 팀원 | 담당 | 역할 |
|------|------|------|
| 정다은 | 백엔드 | Spring Boot 전체, DB, Docker, CI/CD |
| 김혜린 | AI Service | Node.js 서버는 별도 리포. 이 리포의 WebClient로 호출 |
| 안소윤 | 프론트엔드 | Next.js 별도 리포. Swagger 명세 보고 API 연동 |

## Git 브랜치 전략

```
main ← develop ← feature/도메인-기능
                  예: feature/news-crawling
                      feature/policy-recommend
                      feature/spending-eventsourcing
```
