# WEEK4 개발 컨텍스트 — Term Dictionary 완성 + Policy

> 작성: 정다은 / 날짜: 2026-05-04  
> 브랜치: `feature/week4-term-policy`

---

## 이번 주 목표

| 모듈 | 목표 | 결과 |
|------|------|------|
| Term Dictionary | ltree 기반 카테고리, tsvector 검색, 학습 이력 완성 | ✅ |
| Policy | QueryDSL 동적 추천, 즐겨찾기 토글, 마감 알림 스케줄러 | ✅ |
| 테스트 | TermService + PolicyService 단위 테스트 | ✅ (23건) |

---

## 1. Term Dictionary

### 1-1. 엔티티 / 스키마 구조

```
financial_terms
├── id, name, path (LTREE)
├── definition (TEXT)         — 공식 정의
├── simple_definition (TEXT)  — 청년 친화적 쉬운 설명
├── search_vector (TSVECTOR)  — 트리거 자동 생성 (simple 사전)
├── embedding (VECTOR(1536))  — AI Service가 채움 (현재 null)
└── difficulty_level (1~5)
```

```
term_lookup_log
├── user_id, term_id
├── article_id (nullable)  — 뉴스에서 클릭 시 설정
└── looked_up_at
```

```
article_term_highlights
├── article_id, term_id
├── position_start, position_end  — 본문 내 오프셋
└── created_at
```

### 1-2. API 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | /api/terms | 불필요 | 전체 목록 (path 필터, 난이도순) |
| GET | /api/terms/{termId} | 선택 | 상세 (관련 뉴스, 학습 여부) |
| POST | /api/terms/{termId}/learn | 필요 | 학습 완료 처리 |
| GET | /api/terms/search?q= | 불필요 | tsvector 전문 검색 |
| GET | /api/terms/autocomplete?query= | 불필요 | pg_trgm 유사 매칭 |
| GET | /api/terms/category?path= | 불필요 | ltree 하위 용어 조회 |
| GET | /api/terms/level?maxLevel= | 불필요 | 난이도 범위 조회 |

### 1-3. ltree 사용 주의사항

PostgreSQL `ltree` 타입은 JPA 기본 타입이 아니다. 엔티티에서는 `String`으로 매핑하되, 네이티브 쿼리에서 반드시 명시적 캐스팅이 필요하다.

```sql
-- 올바른 방법 (캐스팅 명시)
SELECT * FROM financial_terms WHERE path <@ CAST(:parentPath AS ltree)

-- 잘못된 방법 (ERROR: operator does not exist: ltree <@ text)
SELECT * FROM financial_terms WHERE path <@ :parentPath
```

ltree 연산자:
- `<@` : 하위 경로 포함 (예: `finance.stock.etf <@ finance.stock` → true)
- `@>` : 상위 경로 포함
- `~`  : lquery 패턴 매칭 (예: `finance.*{1}` → 1단계 하위만)

### 1-4. tsvector 검색

`search_vector`는 PostgreSQL 트리거(`trg_terms_search_vector`)가 INSERT/UPDATE 시 자동 계산한다.

```sql
-- 검색 쿼리
SELECT * FROM financial_terms
WHERE search_vector @@ to_tsquery('simple', :query)
ORDER BY ts_rank(search_vector, to_tsquery('simple', :query)) DESC
```

한국어 형태소 분석기(`ko`, `korean`)가 없으므로 `simple` 사전 사용 → 어절 단위 검색. 공백이 있는 복합어는 `&` 연산자로 이어줘야 한다.

### 1-5. 학습 이력 중복 제거

같은 용어를 여러 번 학습하면 `term_lookup_log`에 여러 행이 쌓인다. `getLearnedTerms`에서 `LinkedHashMap`으로 termId 기준 중복 제거한다.

```java
Map<Long, TermLookupLog> latestPerTerm = new LinkedHashMap<>();
for (TermLookupLog log : logs) {
    latestPerTerm.putIfAbsent(log.getTerm().getId(), log);  // 첫 번째(최신)만 유지
}
```

---

## 2. Policy

### 2-1. 엔티티 / 스키마 구조

```
policies
├── id, title, description, summary
├── organization (기관명)
├── region_code (VARCHAR(10), NULL = 전국)
├── age_min, age_max (INTEGER, NULL = 제한 없음)
├── income_criteria (VARCHAR(50))
├── occupation_criteria (JSONB)
├── start_date, end_date (DATE)
├── application_url, source_url
└── embedding (VECTOR(1536))

policy_bookmarks
├── user_id, policy_id (UNIQUE)
└── created_at

policy_notifications
├── user_id, policy_id
├── notify_before_days (기본 7)
├── is_sent (Boolean, 기본 false)
└── notified_at (발송 완료 시각)
```

### 2-2. API 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | /api/policies | 불필요 | 목록 (regionCode 필터, 활성 정책만) |
| GET | /api/policies/{policyId} | 선택 | 상세 |
| GET | /api/policies/recommend | 필요 | 프로필 기반 맞춤 추천 |
| POST | /api/policies/{policyId}/bookmark | 필요 | 즐겨찾기 토글 |

### 2-3. QueryDSL 동적 추천 쿼리

`PolicyQueryRepository.findByProfile`은 4가지 조건을 모두 nullable로 처리한다.

```java
BooleanBuilder where = new BooleanBuilder();
where.and(policy.endDate.isNull().or(policy.endDate.goe(today)));  // 마감 안 된 정책
where.and(policy.ageMin.isNull().or(policy.ageMin.loe(age)));      // 나이 하한
where.and(policy.ageMax.isNull().or(policy.ageMax.goe(age)));      // 나이 상한
if (StringUtils.hasText(regionCode)) {
    where.and(policy.regionCode.isNull().or(policy.regionCode.eq(regionCode)));  // 전국 OR 해당 지역
}
if (StringUtils.hasText(incomeCriteria)) {
    where.and(policy.incomeCriteria.isNull().or(policy.incomeCriteria.eq(incomeCriteria)));
}
```

`regionCode.isNull()` 조건이 없으면 전국 정책이 필터링되어 누락되는 버그가 생긴다.

### 2-4. 마감 알림 스케줄러

`PolicyScheduler`가 매일 09:00에 실행된다. D-7, D-3, D-1 마감 정책을 북마크한 사용자를 찾아 `policy_notifications`에 기록하고, `is_sent = true`로 처리한다.

실제 Push 알림(FCM, APNs) 연동은 Push 서버 구축 후 추가 예정. 현재는 로그 출력으로 대체.

```
흐름:
1. policyRepository.findPoliciesExpiringBetween(targetDate, targetDate) — 오늘 기준 D-N 정책
2. 해당 정책을 북마크한 사용자 조회 (bookmarkRepository.findByPolicyId)
3. 중복 체크 후 policy_notifications INSERT
4. notificationRepository.findUnsentDueNotifications(today) → markAsSent()
```

### 2-5. 나이 계산 fallback

`User.birthDate`가 null이면 `Period.between`을 호출할 수 없어 NPE 발생. `PolicyService.getRecommendedPolicies`에서 null이면 25 사용한다.

```java
int age = user.getBirthDate() != null
        ? Period.between(user.getBirthDate(), LocalDate.now()).getYears()
        : 25;  // 정책 추천용 프로필 미입력 사용자 기본값
```

---

## 3. 테스트

모두 Mockito 단위 테스트 (`@ExtendWith(MockitoExtension.class)`). DB, Redis, Spring Context 없이 실행된다.

| 파일 | 위치 | 케이스 수 |
|------|------|-----------|
| `TermServiceTest` | `domain/term/service/` | 12건 |
| `PolicyServiceTest` | `domain/policy/service/` | 11건 |

### 주요 테스트 케이스

**TermService:**
- `getTermList` — path 유무에 따른 분기 (전체 vs ltree 하위)
- `searchTerms` — 결과 있음 / 없음
- `getTermDetail` — 비로그인(isLearned=false), 로그인+학습됨(isLearned=true), 관련 뉴스 포함, 미존재 예외
- `learnTerm` — 정상 저장, user 미존재, term 미존재
- `getLearnedTerms` — 동일 term 중복 제거, 빈 리스트

**PolicyService:**
- `getPolicyList` — regionCode 유무에 따른 분기
- `getRecommendedPolicies` — 정상 추천, birthDate null 기본값, user 미존재
- `getPolicyDetail` — 비로그인(isBookmarked=false), 로그인+북마크(isBookmarked=true), 미존재 예외
- `toggleBookmark` — 추가(true), 해제(false), user/policy 미존재 예외

---

## 4. 시드 데이터

`docker/postgres/init.sql` 하단에 개발용 시드 데이터가 포함되어 있다.

- 용어 10건 (주식, ETF, 배당, 생명보험, 실손보험, 신용대출, 전세대출, 금리, 인플레이션, 적금)
- term_relations 4건 (ETF→주식 PARENT, 배당→주식 SEE_ALSO, 생명보험↔실손보험 SEE_ALSO, 신용대출↔전세대출 SEE_ALSO)
- 정책 3건 (청년 월세 지원, 청년내일저축계좌, 부산 청년 취업지원금)

DB 재생성이 필요할 때:
```bash
docker-compose -f docker-compose.local.yml down -v
docker-compose -f docker-compose.local.yml up -d
```

---

## 5. SecurityConfig 공개 경로

Term과 Policy의 GET 엔드포인트는 인증 없이 접근 가능하도록 설정되어 있다.

```java
.requestMatchers(
    "/api/terms", "/api/terms/search", "/api/terms/autocomplete",
    "/api/terms/category", "/api/terms/level", "/api/terms/{id}",
    "/api/policies", "/api/policies/{policyId}"
).permitAll()
```

즐겨찾기, 학습 처리, 추천은 JWT 인증 필요.
