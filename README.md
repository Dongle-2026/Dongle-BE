# 유스레터 Backend (Spring Boot)

청년 금융 AI 플랫폼 — Youth Letter 메인 API 서버

## 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.4 |
| Database | PostgreSQL + pgvector | 16 |
| Cache | Redis | 7 |
| ORM | Spring Data JPA + QueryDSL | 5.1.0 |
| Auth | Spring Security + JWT + OAuth2 | — |
| API Docs | springdoc-openapi (Swagger) | 2.8.5 |
| Crawling | Jsoup | 1.18.3 |
| Build | Gradle | 8.12 |
| Container | Docker Compose | — |

## 빠른 시작 (로컬 개발)

### 사전 준비
- Java 21 (IntelliJ에서 자동 설정)
- Docker Desktop 설치
- IntelliJ IDEA

### 1단계: 환경변수 설정
```bash
cp .env.example .env
# .env 파일을 열어서 실제 값 채우기 (로컬 개발 시 기본값으로도 동작)
```

### 2단계: Docker로 인프라 띄우기
```bash
docker-compose -f docker-compose.dev.yml up -d
```
이 명령 하나로:
- PostgreSQL 16 (pgvector 포함) → `localhost:5432`
- Redis 7 → `localhost:6379`
- `docker/postgres/init.sql` 자동 실행 → 20개 테이블 + 인덱스 + 시드 데이터

### 3단계: IntelliJ에서 실행
1. 프로젝트 폴더를 IntelliJ에서 Open
2. Gradle sync 완료 대기
3. `YouthLetterApplication.java` → Run (▶️)
4. 콘솔에서 `Started YouthLetterApplication` 확인

### 4단계: 확인
- 헬스체크: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- API 명세: http://localhost:8080/v3/api-docs

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
    ├── auth/                    # JWT 필터 (TODO)
    └── util/                    # BaseEntity, 헬스체크
```

## API 엔드포인트 (주요)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | `/api/news` | 뉴스 목록 (카테고리 필터, 페이지네이션) | 불필요 |
| GET | `/api/news/{id}` | 뉴스 상세 (원문 + 요약 + 용어 하이라이트) | 불필요 |
| GET | `/api/terms/{id}` | 용어 상세 | 불필요 |
| GET | `/api/terms/search?query=` | 용어 전문 검색 (tsvector) | 불필요 |
| GET | `/api/terms/autocomplete?query=` | 용어 자동완성 (pg_trgm) | 불필요 |
| GET | `/api/terms/category?path=` | 카테고리별 용어 (ltree) | 불필요 |
| GET | `/api/policies/recommend` | 맞춤 정책 추천 | 필요 |
| GET | `/api/policies/{id}` | 정책 상세 | 불필요 |
| POST | `/api/policies/{id}/bookmark` | 정책 즐겨찾기 토글 | 필요 |
| POST | `/api/spending` | 소비 기록 추가 (Event Sourcing) | 필요 |
| PUT | `/api/spending/{spendingId}` | 소비 기록 수정 | 필요 |
| DELETE | `/api/spending/{spendingId}` | 소비 기록 삭제 | 필요 |
| GET | `/api/spending/dashboard` | 월별 소비 대시보드 | 필요 |
| GET | `/api/guides?type=` | 가이드 목록 | 불필요 |

## DB 설계 특이사항 (교수님 설명용)

1. **Temporal Versioning** (`news_summaries`): AI 요약 버전 관리. `valid_from`/`valid_to`로 SCD Type 2 구현
2. **ltree** (`financial_terms.path`): 금융 용어 계층 구조. `<@` 연산자로 O(log N) 하위 조회
3. **Hybrid Search** (`financial_terms`): tsvector(키워드 매칭) + pgvector(의미 유사도) 결합
4. **Event Sourcing** (`spending_events`): INSERT-only. CQRS 패턴으로 읽기/쓰기 분리
5. **RLS** (Row-Level Security): DB 레벨 접근 제어. 코드 버그로 다른 사용자 데이터 접근 원천 차단

## Docker 명령어

```bash
# 로컬 개발 (인프라만)
docker-compose -f docker-compose.dev.yml up -d
docker-compose -f docker-compose.dev.yml down

# 프로덕션 (전체)
docker-compose -f docker-compose.prod.yml up -d --build

# DB 접속
docker exec -it youthletter-postgres psql -U youthletter -d youthletter

# Redis 접속
docker exec -it youthletter-redis redis-cli

# 로그 확인
docker logs -f youthletter-postgres
```

## 팀원별 작업 영역

| 팀원 | 담당 | 이 리포지토리에서의 역할 |
|------|------|----------------------|
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

## CI 테스트 중
