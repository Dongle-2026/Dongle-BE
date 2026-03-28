# CLAUDE.md — 유스레터 백엔드 프로젝트 컨텍스트

## 프로젝트 개요

유스레터(Youth Letter)는 19~34세 청년을 위한 금융 AI 플랫폼.

기존 금융 서비스들이 "이미 아는 사람"을 위해 설계된 반면, 유스레터는 "처음 시작하는 사람"을 위해 설계함.

핵심 차별점: 뉴스 요약 → 용어 클릭 학습 → 정책 추천 → 소비 분석 → 투자/보험 가이드까지를 하나의 흐름으로 연결하여 "학습 → 이해 → 행동"의 여정을 AI로 설계.

## 팀 구성

| 이름 | 역할 | 담당 |
|------|------|------|
| 정다은 | 총괄, 백엔드 | Spring Boot API, DB 설계, Redis, 크롤링, CI/CD |
| 김혜린 | AI Agent | Node.js Express + LangChain, RAG, SSE |
| 안소윤 | 프론트엔드 | Next.js 14 (SSR/CSR), UI 컴포넌트 |

지도교수: 허석렬

## 시스템 아키텍처

```
Next.js (3001) → Spring Boot (8080) → Node.js AI (3000)
                       ↓         ↓
               PostgreSQL (5432)  Redis (6379)
```

- Spring Boot가 유일한 API Gateway. 프론트는 Spring Boot만 호출함.
- Node.js AI Service는 내부 전용. 외부에서 직접 접근하지 않음.
- PostgreSQL 16 + pgvector (벡터 검색) + ltree (계층 구조) + pg_trgm (유사 문자열)
- Redis: 세션, JWT Refresh Token, API 응답 캐시, Rate Limit

## 기술 스택

- Java 17, Spring Boot 3.4.4, Gradle 8.12
- Spring Data JPA + QueryDSL 5.1.0 (Jakarta)
- Spring Security + JWT (jjwt 0.12.6) + OAuth2 (카카오, 구글)
- PostgreSQL 16 + pgvector 0.1.6 + ltree + pg_trgm
- Redis 7
- springdoc-openapi 2.8.5 (Swagger)
- Jsoup 1.18.3 (크롤링)
- WebClient (AI Service 호출)
- Docker Compose (개발/배포)
- GitHub Actions (CI/CD)

## 패키지 구조

```
com.youthletter/
├── config/           설정 (Security, Redis, Swagger, WebClient, JPA)
├── domain/
│   ├── user/         사용자, OAuth, 프로필
│   ├── news/         뉴스 크롤링, AI 요약, 용어 하이라이트
│   ├── term/         금융 용어 사전 (ltree + tsvector + pgvector)
│   ├── policy/       정책 추천, 즐겨찾기, 알림
│   ├── spending/     소비 분석 (Event Sourcing + CQRS)
│   ├── guide/        주식/보험 가이드
│   ├── agent/        AI Agent 채팅 (RAG)
│   └── activity/     읽기 기록, 용어 조회 로그
└── global/
    ├── auth/         JWT 필터 (구현 예정)
    ├── response/     GlobalResponse 공통 래퍼
    ├── exception/    ErrorCode + BusinessException + Handler
    └── util/         BaseEntity, HealthCheck
```

## DB 설계 핵심

- 총 20개 테이블, 17개 인덱스, 6개 트리거, 9개 RLS 정책
- 스키마는 init.sql로 관리 (Hibernate ddl-auto: validate)
- Temporal Versioning: news_summaries (valid_from/valid_to)
- Event Sourcing: spending_events (INSERT ONLY, append-only)
- CQRS: spending_events(쓰기) + spending_snapshots(읽기)
- Hybrid Search: financial_terms에 tsvector(키워드) + pgvector(의미 유사도)
- ltree: financial_terms.path (finance.stock.etf)
- RLS: user_id 기반 행 수준 접근 제어

## 응답 형식

모든 API는 GlobalResponse<T>로 통일:

```json
{ "status": 200, "message": "ok", "data": { ... } }
```

에러 코드: 40xx(인증), 41xx(사용자), 42xx(뉴스), 43xx(용어), 44xx(정책), 45xx(AI Agent), 50xx(서버)

## 현재 상태

완료: ERD, init.sql, 엔티티(20개), 리포지토리, 컨트롤러(News/Term/Policy/Spending/Guide), 서비스(News/Policy/Spending/Activity), DTO, Docker Compose, CI/CD, Swagger

미완료: JWT 인증 필터, 회원가입/로그인, 뉴스 크롤링, AI Service 연동, 소비 배치, 정책 배치, AI Agent SSE

## 코딩 규칙

- 엔티티: @NoArgsConstructor(PROTECTED) + @Builder, Setter 사용 금지
- 조회: @Transactional(readOnly = true)
- N+1 방지: default_batch_fetch_size: 100 + 필요 시 fetch join
- DTO: Java record 사용 (불변)
- 네이티브 쿼리: pgvector, ltree, tsvector 연산은 네이티브 쿼리 사용
- 에러: BusinessException(ErrorCode) 던지기, GlobalExceptionHandler에서 처리

## 로컬 실행

```bash
docker-compose -f docker-compose.dev.yml up -d   # DB + Redis
# IntelliJ에서 YouthLetterApplication.java Run
# http://localhost:8080/swagger-ui/index.html
# http://localhost:8080/actuator/health
```

## Git 브랜치

```
main ← develop ← feature/기능명
```
