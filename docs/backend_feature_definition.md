# 백엔드 즉시 개발 가능 기능정의서

> **작성일:** 2026-04-08  
> **작성자:** 정다은  
> **기준:** AI 서비스(Node.js) 미완성 상태에서도 Spring Boot 단독으로 개발 가능한 기능 전체  
> **원칙:** AI 연동이 필요한 기능은 `AiServiceClient` stub mode로 처리하여 병렬 개발 가능

---

## 개발 가능 판단 기준

| 구분 | 설명 |
|------|------|
| ✅ 즉시 개발 | AI 서비스 의존 없음. 단독 개발 가능 |
| 🔶 stub 개발 | AI 응답이 필요하나 stub으로 대체 → 나중에 실제 연동으로 교체 |
| ❌ 대기 | AI 서비스 API 확정 전까지 보류 |

---

## 모듈 1. Auth (인증/인가)

> AI 의존 없음. 전부 즉시 개발 가능.

| 기능 ID | 기능명 | 메서드 | 엔드포인트 | 설명 | 상태 |
|---------|--------|--------|-----------|------|------|
| AUTH-01 | 이메일 회원가입 | POST | `/api/auth/signup` | 이메일+비밀번호 회원가입, 비밀번호 BCrypt 암호화 | ✅ |
| AUTH-02 | 이메일 로그인 | POST | `/api/auth/login` | JWT Access/Refresh Token 발급 | ✅ |
| AUTH-03 | 카카오 소셜 로그인 | GET | `/api/auth/oauth2/kakao` | OAuth2 카카오 인가 코드 → JWT 발급 | ✅ |
| AUTH-04 | 구글 소셜 로그인 | GET | `/api/auth/oauth2/google` | OAuth2 구글 인가 코드 → JWT 발급 | ✅ |
| AUTH-05 | 토큰 재발급 | POST | `/api/auth/reissue` | Refresh Token → 새 Access Token 발급 (Redis 검증) | ✅ |
| AUTH-06 | 로그아웃 | POST | `/api/auth/logout` | Redis에서 Refresh Token 삭제 | ✅ |
| AUTH-07 | 회원 탈퇴 | DELETE | `/api/auth/withdraw` | 계정 비활성화 처리 (soft delete) | ✅ |

**기술 상세**
- Spring Security + JWT (Access Token 15분 / Refresh Token 7일)
- Refresh Token → Redis 저장 (`RT:{userId}` 키)
- OAuth2 콜백 처리: `OAuth2SuccessHandler` → JWT 발급 후 프론트 리다이렉트

---

## 모듈 2. User Profile (사용자 프로필)

> AI 의존 없음. 전부 즉시 개발 가능.

| 기능 ID | 기능명 | 메서드 | 엔드포인트 | 설명 | 상태 |
|---------|--------|--------|-----------|------|------|
| USER-01 | 내 프로필 조회 | GET | `/api/users/me` | 이름, 이메일, 관심 카테고리, 프로필 이미지 등 반환 | ✅ |
| USER-02 | 프로필 수정 | PATCH | `/api/users/me` | 닉네임, 프로필 이미지 URL 수정 | ✅ |
| USER-03 | 관심 카테고리 설정 | PUT | `/api/users/me/categories` | 경제/투자/보험/대출/정책/청년금융 중 다중 선택 | ✅ |
| USER-04 | 정책 추천용 프로필 설정 | PUT | `/api/users/me/policy-profile` | 나이, 거주 지역, 취업 여부, 소득 구간 입력 | ✅ |
| USER-05 | 학습 진행 현황 조회 | GET | `/api/users/me/learning-stats` | 읽은 뉴스 수, 학습한 용어 수, 연속 학습일 등 | ✅ |

---

## 모듈 3. News (뉴스)

> 크롤링·저장·조회는 즉시 개발 가능. AI 요약/NER 결과 저장은 stub 처리.

| 기능 ID | 기능명 | 메서드 | 엔드포인트 | 설명 | 상태 |
|---------|--------|--------|-----------|------|------|
| NEWS-01 | 뉴스 목록 조회 | GET | `/api/news` | 카테고리/날짜/키워드 필터, 페이지네이션 | ✅ |
| NEWS-02 | 뉴스 상세 조회 | GET | `/api/news/{newsId}` | 원문, AI 3줄 요약(stub), NER 용어(stub) 포함 | 🔶 |
| NEWS-03 | 뉴스 읽음 처리 | POST | `/api/news/{newsId}/read` | 읽음 이력 저장 (user_news_read 테이블) | ✅ |
| NEWS-04 | 뉴스 북마크 | POST | `/api/news/{newsId}/bookmark` | 북마크 추가/취소 토글 | ✅ |
| NEWS-05 | 북마크 목록 조회 | GET | `/api/users/me/bookmarks` | 내 북마크 뉴스 목록 | ✅ |
| NEWS-06 | 오늘의 뉴스 홈 브리핑 | GET | `/api/news/today` | 오늘 날짜 기준 최신 12건, 관심 카테고리 우선 정렬 | ✅ |
| NEWS-07 | 뉴스 크롤링 (스케줄러) | 내부 | - | @Scheduled(07/12/18시), Jsoup으로 한경 파싱 | ✅ |
| NEWS-08 | 뉴스 크롤링 수동 트리거 | POST | `/api/admin/crawl` | 개발/테스트용 즉시 크롤링 실행 | ✅ |
| NEWS-09 | 크롤링 처리 상태 조회 | GET | `/api/admin/crawl/status` | 마지막 실행 시각, 처리 건수, 실패 건수 | ✅ |

**기술 상세**
- `HankyungCrawlerService`: Jsoup HTML 파싱 → `NewsProcessingService` 호출
- `NewsRepository`: pgvector native query (벡터 유사도 검색용, 임베딩은 AI stub 처리 후 저장)
- `original_url` UNIQUE constraint로 중복 크롤링 방지
- AI 요약·NER 결과 컬럼: DB에 nullable로 설계, stub 시엔 `null` 또는 mock 데이터 반환

---

## 모듈 4. Term Dictionary (용어 사전)

> NER 결과 저장/조회는 독립적으로 구현 가능.

| 기능 ID | 기능명 | 메서드 | 엔드포인트 | 설명 | 상태 |
|---------|--------|--------|-----------|------|------|
| TERM-01 | 용어 상세 조회 | GET | `/api/terms/{termId}` | 용어명, 설명, 관련 뉴스 목록 반환 | ✅ |
| TERM-02 | 용어 목록 조회 | GET | `/api/terms` | 전체 용어 목록, 카테고리 필터 | ✅ |
| TERM-03 | 용어 학습 완료 처리 | POST | `/api/terms/{termId}/learn` | 학습 이력 저장 (user_term_learn 테이블) | ✅ |
| TERM-04 | 내가 학습한 용어 목록 | GET | `/api/users/me/learned-terms` | 학습 날짜순 정렬 | ✅ |
| TERM-05 | 용어 검색 | GET | `/api/terms/search?q=` | 용어명 LIKE 검색 또는 tsvector 전문 검색 | ✅ |

---

## 모듈 5. Policy (청년 정책)

> 외부 정부 API 연동 + DB 저장은 AI와 무관. 즉시 개발 가능.

| 기능 ID | 기능명 | 메서드 | 엔드포인트 | 설명 | 상태 |
|---------|--------|--------|-----------|------|------|
| POLICY-01 | 정책 목록 조회 | GET | `/api/policies` | 카테고리/지역/마감일 필터, 페이지네이션 | ✅ |
| POLICY-02 | 정책 상세 조회 | GET | `/api/policies/{policyId}` | 지원 내용, 자격 요건, 신청 URL, 마감일 | ✅ |
| POLICY-03 | 프로필 기반 맞춤 정책 추천 | GET | `/api/policies/recommend` | USER-04 프로필 기반 QueryDSL 동적 쿼리 필터링 | ✅ |
| POLICY-04 | 정책 북마크 | POST | `/api/policies/{policyId}/bookmark` | 관심 정책 저장/취소 토글 | ✅ |
| POLICY-05 | 정책 마감 알림 스케줄러 | 내부 | - | @Scheduled 매일 09시, D-7/D-3/D-1 알림 생성 | ✅ |
| POLICY-06 | 정책 데이터 수집 스케줄러 | 내부 | - | 정부 공공 API 주기적 수집 → DB upsert | ✅ |

**기술 상세**
- QueryDSL 동적 쿼리: 나이/지역/취업 여부/소득 구간 조건 조합
- 마감일 기반 `@Scheduled`: `policy.deadline BETWEEN now() AND now()+7`

---

## 모듈 6. Spending (소비 분석)

> 소비 CRUD 및 집계는 AI 없이 구현 가능. AI 인사이트만 stub.

| 기능 ID | 기능명 | 메서드 | 엔드포인트 | 설명 | 상태 |
|---------|--------|--------|-----------|------|------|
| SPEND-01 | 소비 내역 등록 | POST | `/api/spending` | 금액, 카테고리, 날짜, 메모 입력 | ✅ |
| SPEND-02 | 소비 내역 수정 | PATCH | `/api/spending/{spendingId}` | 금액/카테고리/메모 수정 | ✅ |
| SPEND-03 | 소비 내역 삭제 | DELETE | `/api/spending/{spendingId}` | soft delete | ✅ |
| SPEND-04 | 월별 소비 목록 조회 | GET | `/api/spending?year=&month=` | 해당 월 전체 소비 내역 | ✅ |
| SPEND-05 | 카테고리별 집계 | GET | `/api/spending/summary` | 월별 카테고리 합계, SQL SUM + GROUP BY | ✅ |
| SPEND-06 | 월간 소비 트렌드 | GET | `/api/spending/trend` | 최근 6개월 월별 총액 변화, SQL 윈도우 함수 (LAG) | ✅ |
| SPEND-07 | 소비 예산 설정 | POST | `/api/spending/budget` | 월 예산 목표 금액 설정 | ✅ |
| SPEND-08 | 예산 달성률 조회 | GET | `/api/spending/budget/status` | 이번 달 소비 / 예산 목표 비율 | ✅ |
| SPEND-09 | AI 소비 인사이트 요청 | POST | `/api/spending/ai-insight` | 소비 집계 데이터 → AI Service 전송 후 인사이트 반환 | 🔶 |

**기술 상세**
- 월간 트렌드: `LAG(total_amount) OVER (ORDER BY month)` native query
- SPEND-09 stub: 고정 텍스트 mock 응답으로 API 인터페이스 먼저 완성

---

## 모듈 7. Stock (주식 시세)

> 외부 API + Redis 캐싱 구조. AI 의존 없음.

| 기능 ID | 기능명 | 메서드 | 엔드포인트 | 설명 | 상태 |
|---------|--------|--------|-----------|------|------|
| STOCK-01 | 주식 시세 조회 | GET | `/api/stocks/{ticker}` | 외부 주가 API → Redis 5분 TTL 캐싱 후 반환 | ✅ |
| STOCK-02 | 관심 종목 등록 | POST | `/api/stocks/watchlist` | 종목 코드 저장 (user_watchlist 테이블) | ✅ |
| STOCK-03 | 관심 종목 목록 조회 | GET | `/api/stocks/watchlist` | 내 관심 종목 + 현재가 | ✅ |
| STOCK-04 | 관심 종목 삭제 | DELETE | `/api/stocks/watchlist/{ticker}` | 관심 종목 제거 | ✅ |

---

## 모듈 8. Notification (알림)

> AI 의존 없음. 스케줄러 기반 알림 생성.

| 기능 ID | 기능명 | 메서드 | 엔드포인트 | 설명 | 상태 |
|---------|--------|--------|-----------|------|------|
| NOTI-01 | 알림 목록 조회 | GET | `/api/notifications` | 읽지 않은 알림 우선 정렬 | ✅ |
| NOTI-02 | 알림 읽음 처리 | PATCH | `/api/notifications/{notiId}/read` | 단건 읽음 처리 | ✅ |
| NOTI-03 | 전체 알림 읽음 처리 | PATCH | `/api/notifications/read-all` | 전체 읽음 처리 | ✅ |
| NOTI-04 | 정책 마감 알림 생성 | 내부 | - | @Scheduled D-7/D-3/D-1 기준 알림 row 생성 | ✅ |

---

## 모듈 9. Admin / Internal

> 개발·운영 편의용. 전부 즉시 구현 가능.

| 기능 ID | 기능명 | 메서드 | 엔드포인트 | 설명 | 상태 |
|---------|--------|--------|-----------|------|------|
| ADMIN-01 | 크롤링 수동 실행 | POST | `/api/admin/crawl` | 스케줄러 대기 없이 즉시 크롤링 트리거 | ✅ |
| ADMIN-02 | 크롤링 상태 조회 | GET | `/api/admin/crawl/status` | 마지막 실행 시각, 성공/실패 건수 | ✅ |
| ADMIN-03 | AI stub 모드 토글 | POST | `/api/admin/ai-stub` | stub 모드 on/off (개발 환경 전용) | ✅ |
| ADMIN-04 | Health Check | GET | `/api/health` | DB, Redis 연결 상태 확인 | ✅ |

---

## 전체 현황 요약

| 모듈 | 전체 기능 수 | ✅ 즉시 개발 | 🔶 stub 개발 | ❌ 대기 |
|------|------------|------------|------------|--------|
| Auth | 7 | 7 | 0 | 0 |
| User Profile | 5 | 5 | 0 | 0 |
| News | 9 | 7 | 2 | 0 |
| Term Dictionary | 5 | 5 | 0 | 0 |
| Policy | 6 | 6 | 0 | 0 |
| Spending | 9 | 8 | 1 | 0 |
| Stock | 4 | 4 | 0 | 0 |
| Notification | 4 | 4 | 0 | 0 |
| Admin | 4 | 4 | 0 | 0 |
| **합계** | **53** | **50** | **3** | **0** |

> stub 대상 3건 (NEWS-02, SPEND-09, 내부 임베딩 저장)은  
> `AiServiceClient.isStubMode() == true` 조건으로 분기 처리 → AI 연동 완료 시 stub 코드만 제거

---

## 개발 우선순위 권장 순서

```
1단계 (기반)     : AUTH → USER → DB 마이그레이션 (Flyway)
2단계 (핵심 MVP) : NEWS-01~09 (크롤링 + 목록/상세) → TERM-01~05
3단계 (차별화)   : POLICY-01~06 → SPEND-01~08
4단계 (부가)     : STOCK → NOTI → ADMIN
5단계 (AI 연동)  : stub → 실제 AiServiceClient 교체 (NEWS-02, SPEND-09)
```
