# SKILL.md — 유스레터 백엔드 구현 명세

## 목적

백엔드(Spring Boot)가 구현해야 할 기능을 우선순위별로 정리함.

각 기능의 목적, 관련 테이블, 구현 방향, 주의 사항을 포함.

---

## 구현 우선순위 총괄

```
Phase 1 — 인증 기반 (Spring Boot 단독)
  ├── 1. JWT 인증 필터
  └── 2. 회원가입 / 로그인 / OAuth2

Phase 2 — 데이터 수집 (Spring Boot 단독)
  ├── 3. 뉴스 크롤링 파이프라인
  ├── 4. 정책 수집 배치
  └── 5. Activity 추적 API

Phase 3 — 데이터 가공 (Spring Boot 단독)
  ├── 6. 소비 스냅샷 배치
  └── 7. 정책 알림 배치

Phase 4 — AI 연동 (Node.js 서비스 필요)
  ├── 8. AI Service 호출 클라이언트
  ├── 9. 뉴스 AI 처리 (요약 + NER + 임베딩)
  └── 10. AI Agent 오케스트레이션 (SSE)
```

Phase 1~3은 Node.js AI 서비스 없이 진행 가능.

Phase 4부터 혜린과 내부 API 규격 합의 필요.

---

## Phase 1 — 인증 기반

### 1. JWT 인증 필터

**왜 가장 먼저인가**: 정책 추천, 소비 분석, AI Agent 등 "인증 필요" API가 전부 이게 없으면 동작하지 않음.

**현재 상태**: SecurityConfig에 TODO로 표시. 컨트롤러에서 @RequestParam Long userId로 임시 처리 중.

**관련 파일**:
- 생성: global/auth/JwtTokenProvider.java
- 생성: global/auth/JwtAuthenticationFilter.java
- 생성: global/auth/CustomUserDetails.java
- 수정: config/SecurityConfig.java (필터 등록)

**구현 상세**:

JwtTokenProvider
- generateAccessToken(userId, email) → 15분 만료
- generateRefreshToken(userId) → 7일 만료, Redis에 저장
- validateToken(token) → boolean
- getUserIdFromToken(token) → Long
- 서명 알고리즘: HS256
- 시크릿: application.yml의 jwt.secret

JwtAuthenticationFilter (OncePerRequestFilter 상속)
- 매 요청 → Authorization 헤더에서 "Bearer {token}" 추출
- JwtTokenProvider.validateToken() 호출
- 유효하면 SecurityContext에 Authentication 세팅
- 유효하지 않으면 그냥 다음 필터로 넘김 (permitAll 경로면 통과)

SecurityConfig 수정
- addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

컨트롤러 변경
- @RequestParam Long userId 제거
- @AuthenticationPrincipal CustomUserDetails userDetails 로 교체
- userDetails.getUserId()로 userId 추출

**Redis Refresh Token 관리**:
- Key: "refresh:{userId}"
- Value: refreshToken 문자열
- TTL: 7일
- 로그아웃 시 삭제
- 토큰 갱신 시 기존 것 삭제 후 새로 저장

---

### 2. 회원가입 / 로그인 / OAuth2

**관련 파일**:
- 생성: domain/user/controller/AuthController.java
- 생성: domain/user/service/AuthService.java
- 생성: domain/user/dto/SignupRequest.java
- 생성: domain/user/dto/LoginRequest.java
- 생성: domain/user/dto/TokenResponse.java
- 생성: global/auth/OAuth2SuccessHandler.java

**엔드포인트**:

```
POST /api/auth/signup        이메일 회원가입
POST /api/auth/login         이메일 로그인
POST /api/auth/refresh       Access Token 갱신
POST /api/auth/logout        로그아웃 (Refresh Token 삭제)
GET  /oauth2/authorization/kakao   카카오 로그인 시작
GET  /oauth2/authorization/google  구글 로그인 시작
```

**회원가입 로직**:
1. 이메일/닉네임 중복 체크 (4101, 4102 에러)
2. 비밀번호 BCrypt 암호화
3. users 테이블 INSERT
4. user_profiles 테이블 INSERT (빈 프로필)
5. Access Token + Refresh Token 발급

**OAuth2 로그인 로직**:
1. 카카오/구글 인증 → OAuth2SuccessHandler 호출
2. provider + provider_id로 oauth_accounts 조회
3. 있으면 → 해당 user로 JWT 발급
4. 없으면 → users INSERT + oauth_accounts INSERT → JWT 발급
5. password_hash는 NULL (OAuth 전용)

---

## Phase 2 — 데이터 수집

### 3. 뉴스 크롤링 파이프라인

**관련 파일**:
- 생성: domain/news/service/NewsCrawlerService.java

**스케줄**: @Scheduled(cron = "0 0 7,12,18 * * *") — 하루 3회 (07시, 12시, 18시)

**크롤링 대상 (초기)**:
- 한국경제 금융 섹션
- 매일경제 금융 섹션
- 추후 확장 가능

**로직**:
1. Jsoup으로 뉴스 목록 페이지 HTML 파싱
2. 각 기사의 URL, 제목, 본문, 카테고리, 발행일 추출
3. source_url로 중복 체크 (existsBySourceUrl)
4. 중복 아니면 news_articles INSERT
5. (Phase 4) AI Service에 요약/NER/임베딩 요청

**주의**:
- robots.txt 확인
- 크롤링 간격 1~2초 딜레이 (Thread.sleep 또는 Rate Limiter)
- HTML 구조 변경 시 파서 깨질 수 있음 → 에러 로깅 + 알림
- source_url UNIQUE 제약이 최종 안전장치

**Phase 2에서는 AI 없이 DB 저장까지만 구현. 요약/NER은 Phase 4에서 붙임.**

---

### 4. 정책 수집 배치

**관련 파일**:
- 생성: domain/policy/service/PolicyCrawlerService.java

**스케줄**: @Scheduled(cron = "0 0 3 * * MON") — 매주 월요일 새벽 3시

**데이터 소스**:
- 정부24 API (공공데이터포털)
- 온통청년 API
- 각 지자체 청년 정책 페이지 (크롤링)

**로직**:
1. 외부 API 호출 (externalApiWebClient)
2. 응답 파싱 → title, description, organization, age_min/max, region_code 등 추출
3. source_url로 중복 체크
4. 신규면 INSERT, 기존이면 UPDATE (마감일 변경 등)
5. (Phase 4) AI Service에 요약 + 임베딩 요청

---

### 5. Activity 추적 API

**현재 상태**: ActivityService는 구현됨. 컨트롤러만 없음.

**관련 파일**:
- 생성: domain/activity/controller/ActivityController.java

**엔드포인트**:

```
POST /api/activity/reading       뉴스 읽기 기록
  - body: { articleId, durationSeconds }
  - 프론트에서 뉴스 상세 이탈 시 호출

POST /api/activity/term-lookup   용어 조회 기록
  - body: { termId, articleId(nullable) }
  - 프론트에서 용어 팝업 클릭 시 호출
```

두 엔드포인트 모두 인증 필요 (Phase 1 완료 후 가능).

---

## Phase 3 — 데이터 가공

### 6. 소비 스냅샷 배치

**관련 파일**:
- 생성: domain/spending/service/SpendingBatchService.java

**스케줄**: @Scheduled(cron = "0 0 2 * * *") — 매일 새벽 2시

**로직**:
1. 전체 사용자 목록 조회
2. 사용자별로 전월 spending_events 집계 (aggregateByCategory 네이티브 쿼리)
3. spending_snapshots에 UPSERT
   - 이미 있으면 → category_totals, total_amount 업데이트
   - 없으면 → INSERT

**주의**:
- 사용자 수가 많아지면 배치 시간이 길어질 수 있음
- 청크 단위로 처리하거나, 변경이 있는 사용자만 대상으로 최적화 가능
- spending_snapshots의 UNIQUE(user_id, snapshot_month)로 중복 방지

---

### 7. 정책 알림 배치

**관련 파일**:
- 생성: domain/policy/service/PolicyNotificationService.java

**스케줄**: @Scheduled(cron = "0 0 9 * * *") — 매일 오전 9시

**로직**:
1. policy_notifications에서 is_sent = false인 항목 조회
2. 각 알림의 policy.end_date - notify_before_days <= 오늘인지 확인
3. 조건 충족 시 알림 발송 + is_sent = true + notified_at = NOW()

**MVP 알림 방식**:
- 앱 내 알림 (프론트에서 GET /api/notifications로 폴링)
- 추후 FCM Push, 이메일 등 확장 가능

---

## Phase 4 — AI 연동

### 8. AI Service 호출 클라이언트

**혜린과 합의 필요한 내부 API 규격**:

```
POST http://ai-service:3000/ai/summarize
  요청: { "content": "뉴스 본문..." }
  응답: { "summary": "3줄 요약..." }

POST http://ai-service:3000/ai/extract-terms
  요청: { "content": "뉴스 본문..." }
  응답: { "terms": [{ "name": "ETF", "start": 42, "end": 45 }] }

POST http://ai-service:3000/ai/embed
  요청: { "text": "텍스트..." }
  응답: { "embedding": [0.012, -0.034, ...] }  // 1536차원

POST http://ai-service:3000/ai/chat (SSE)
  요청: { "sessionId": 1, "message": "ETF가 뭐야?", "context": [...] }
  응답: SSE 스트림 (토큰 단위)
```

**관련 파일**:
- 생성: domain/news/service/AiServiceClient.java

**구현**:
- WebClient 기반
- 타임아웃: 30초 (요약), 60초 (Agent)
- 에러 시 ErrorCode.AI_SERVICE_ERROR (5001) 또는 AI_SERVICE_TIMEOUT (5002)
- 응답 캐싱: NER 결과는 Redis TTL 24h로 캐싱 (API 비용 절감)

---

### 9. 뉴스 AI 처리 파이프라인

Phase 2의 크롤링 후 AI 처리를 붙이는 단계.

**로직**:
1. 크롤링 완료된 기사 조회 (요약이 없는 기사: findArticleIdsWithoutActiveSummary)
2. 각 기사에 대해:
   a. /ai/summarize → news_summaries INSERT
   b. /ai/extract-terms → article_term_highlights INSERT (용어 위치 매핑)
   c. /ai/embed → financial_terms.embedding UPDATE (네이티브 쿼리)
3. 기존 요약이 있으면 Temporal Versioning: 기존 valid_to = NOW() → 새 행 INSERT

**주의**:
- OpenAI API 호출 비용 → NER 결과 Redis 캐싱으로 90%+ 절감
- 한 기사 처리 실패해도 다른 기사에 영향 없도록 개별 try-catch

---

### 10. AI Agent 오케스트레이션 (SSE)

Spring Boot가 프론트와 Node.js AI Service 사이에서 중계하는 역할.

**관련 파일**:
- 생성: domain/agent/controller/AgentController.java
- 생성: domain/agent/service/AgentService.java

**엔드포인트**:

```
POST /api/agent/chat
  Content-Type: application/json
  Accept: text/event-stream
  body: { "sessionId": 1, "message": "청년 전세 대출 알려줘" }
  응답: SSE 스트림
```

**로직**:
1. 세션 조회 (없으면 자동 생성)
2. 사용자 메시지를 chat_messages INSERT (role: USER)
3. AI Service에 SSE 요청
4. AI 응답 토큰을 SseEmitter로 프론트에 중계
5. 스트림 완료 후 전체 응답을 chat_messages INSERT (role: ASSISTANT, tool_calls, sources 포함)

**SseEmitter 사용**:
```java
@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter(60000L); // 60초 타임아웃
    agentService.streamChat(request, emitter);
    return emitter;
}
```

---

## 도메인별 관련 테이블 매핑

| 기능 | 읽는 테이블 | 쓰는 테이블 |
|------|-----------|-----------|
| JWT 인증 | users | - (Redis에 Refresh Token) |
| 회원가입 | - | users, user_profiles, oauth_accounts |
| 뉴스 크롤링 | news_articles (중복 체크) | news_articles |
| 뉴스 AI 처리 | news_articles | news_summaries, article_term_highlights |
| 정책 수집 | policies (중복 체크) | policies |
| 정책 알림 | policy_notifications, policies | policy_notifications |
| 소비 스냅샷 | spending_events | spending_snapshots |
| AI Agent | chat_sessions, chat_messages | chat_sessions, chat_messages |
| Activity | - | reading_history, term_lookup_log |

---

## 공통 주의 사항

### 트랜잭션
- 조회 전용 메서드: @Transactional(readOnly = true)
- 쓰기 메서드: @Transactional
- 배치 작업: 청크 단위로 트랜잭션 분리 (한 건 실패해도 전체 롤백 방지)

### 에러 처리
- 비즈니스 에러: throw new BusinessException(ErrorCode.XXX)
- AI 서비스 에러: WebClient onStatus로 5001/5002 매핑
- 예상치 못한 에러: GlobalExceptionHandler에서 5000으로 처리

### 캐싱 (Redis)
- NER 결과: TTL 24h (API 비용 절감)
- 주식 시세: TTL 5분
- 뉴스 목록: TTL 10분
- Key 네이밍: "도메인:식별자" (예: "ner:article:123", "stock:005930")

### 네이티브 쿼리가 필요한 경우
- pgvector 코사인 유사도: embedding <=> CAST(:vec AS vector)
- ltree 하위 조회: path <@ CAST(:path AS ltree)
- tsvector 검색: search_vector @@ to_tsquery('simple', :query)
- Event Sourcing 집계: DISTINCT ON (spending_id) + GROUP BY category

### 로깅
- 크롤링 성공/실패: INFO/ERROR
- AI 서비스 호출: DEBUG (요청/응답 요약)
- 배치 실행 결과: INFO (처리 건수, 소요 시간)
- 에러: ERROR (스택트레이스 포함)
