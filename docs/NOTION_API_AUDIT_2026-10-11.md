# 노션 API 29개 대조·실행 검증 결과

검증일: 2026-10-11  
대상 코드: e2b787f 기준 작업 트리. 사용자가 삭제한 기존 문서 4개는 변경/복구하지 않았다.  
원문: [노션에서 내보낸 명세](api-audit/NOTION_SOURCE_2026-10-08.md), 문서 표기일 2026-10-08.

## 1. 결론

**노션 명세 그대로 호출해 모두 정상 동작하는 상태가 아니다.**

- 29개 중 **메서드·경로가 일치하는 것은 4개**: A3 로그아웃, A4 뉴스 목록, A5 뉴스 상세, A14 읽음.
- 나머지 **25개는 그 메서드·경로로 등록되어 있지 않다.** 일부는 다른 경로에 기능이 있지만, 퀴즈·포인트 등은 신규 구현이 필요하다.
- 정확한 경로를 인증 상태로 요청한 결과: **200 3개, 500 1개, 404 24개, 400 1개**. 200인 경우도 전체 계약 일치라는 뜻은 아니다.
- 뉴스 목록 A4에서 PostgreSQL **lower(bytea) 오류와 HTTP 500**을 재현했다.
- A14는 HTTP 200이지만 같은 기사 두 번 읽음 처리 시 기록 2개·readNewsCount=2가 되어 **멱등성 요구에 불합격**이다.
- 페이지 번호/limit, 응답 목록 구조, ID 타입, 날짜 오프셋, 공개 범위, 오류 형태가 공통 규약과 다르다.

이번에는 **검증용 테스트와 문서만 추가**했다. 실제 API 코드·DB 스키마·노션 원문은 수정하지 않았다. 계약 변경과 신규 기능 구현은 별도 작업으로 진행해야 한다.

## 2. 자료의 범위와 검증 방법

ZIP에는 Markdown 1개와 API 목록 CSV 2개(일반/전체)가 있다. 개별 API별 상세 요청/응답 페이지 29개는 포함되어 있지 않다. 따라서 공통 규약과 경로·용도·우선순위를 기준으로 확인했다. 제공되지 않은 세부 DTO를 추측해 '일치'로 판정하지 않았다.

ZIP SHA-256:

`1d57c4f4d785754409ab86d3f3d548246ac20c7b9c96774f974cb506e8e24dc9`

Markdown의 P1/P2와 CSV의 높음/중간/낮음이 일부 다르다. 예를 들어 A15·A20·A21은 Markdown P1이나 CSV 높음이다. 아래의 우선순위는 **Markdown의 P0/P1/P2**를 사용한다.

검증 단계:

1. Spring의 실제 RequestMappingHandlerMapping에서 메서드+경로를 조회했다. 변수 이름만 정규화하여 {articleId}와 {id}를 같은 것으로 보았다.
2. 실제 Spring Security/JWT·Controller·Service·JPA를 통과하는 **MockMvc 통합 요청**을 실행했다. Service를 가짜 응답으로 대체하지 않았다.
3. PostgreSQL **localhost:5433/youthletter_test**, Redis **localhost:6380**만 사용했다. 자동 크롤링·AI 작업자·정책 스케줄은 비활성화했다.
4. 가상의 사용자·기사·용어를 넣고 DB 변경을 테스트 트랜잭션으로 rollback했다. 이 테스트의 Refresh/blacklist 키만 정리했다. DB sequence 번호는 rollback되지 않아 건너뛸 수 있다.
5. 실제 제공자 OAuth, 원격 푸시, 실제 AI 추론, 휴대폰 네트워크는 이번 명세 검증에 포함하지 않았다.

원본 15432 DB와 보존용 복제 DB는 열거나 변경하지 않았다. 테스트 컨테이너 두 개만 기존 정지 상태에서 시작했다.

**MockMvc의 HTTP 상태/JSON 검증이지, 실제 TCP 서버나 iPhone에서 실행한 결과는 아니다.** 누락 경로는 유효한 테스트 JWT를 붙여 확인했다. 익명 요청의 401만 보고 API 존재 여부를 판단하지 않았다.

## 3. API별 결과 — 노션에 옮길 표

'대체 경로'는 자동 별칭이 아니다. 앱이 해당 경로·입출력에 맞게 바꾸거나 서버에 명세용 호환 계층을 추가해야 한다.

| ID | 우선 | 노션 Method · Path | 실행 HTTP | 현재 구현 / 판정 | 해야 할 일 |
|---|---|---|---|---|---|
| A1 | P0 | POST /api/auth/oauth/{provider} | 404 | 없음. 기존 브라우저 OAuth 흐름은 별개 | 입력이 provider code인지 idToken인지 확정, 모바일 인증·코드 교환·콜백 구현 |
| A2 | P0 | POST /api/auth/refresh | 404 | POST /api/auth/reissue로 기능 존재·갱신 검증 | 경로 통일 또는 호환 경로 추가, 세부 요청/응답 확정 |
| A3 | P0 | POST /api/auth/logout | 200 | 일치. 인증 사용자 로그아웃 성공 | 응답 규약 문서화, 기존 세션 정책 유지/변경 합의 |
| A4 | P0 | GET /api/news | 500 | 경로 존재. 일부 무검색어 호출에서 SQL 오류 | null 검색어 타입 처리 수정 + 모든 필터 조합 회귀; 목록 계약 변환 |
| A5 | P0 | GET /api/news/{id} | 200 | 경로 존재. 현재 원문·요약·용어까지 포함 | 상세/본문 분리 여부 확정; 숫자 ID·무오프셋 시간 변환 |
| A6 | P0 | POST /api/news/{id}/save | 404 | PUT /api/news/{id}/bookmark로 멱등 저장 가능·검증 | 새 경로로 연결하거나 명세용 별칭 추가 |
| A7 | P0 | POST /api/news/{id}/unsave | 404 | DELETE /api/news/{id}/bookmark로 멱등 해제 가능·검증 | A6와 같이 계약 통일 |
| A8 | P0 | GET /api/me | 404 | GET /api/users/me 조회 성공 | 프로필 필드 목록 확정, 경로/ID 통일 |
| A9 | P0 | GET /api/me/stats | 404 | GET /api/users/me/learning-stats 일부 제공 | 오늘 완료 여부·포인트·KST 기준 추가; 읽음 중복 카운트 수정 |
| A10 | P0 | GET /api/news/{id}/body | 404 | 현재 상세에 본문·용어 포함, 퀴즈·내 답안 없음 | 본문 DTO·퀴즈·사용자 답안 조회 신규 구성 |
| A11 | P0 | POST /api/quizzes/{quizId}/answer | 404 | 퀴즈 제출·꿀 지급 도메인/경로 없음 | 퀴즈/답안/지급 원장/중복 방지/채점/재도전 정책 구현 |
| A12 | P0 | POST /api/terms/{id}/save | 404 | POST /api/terms/{id}/learn은 학습 이력이며 저장과 다름 | 독립 용어 북마크가 필요한지 확정 후 저장 구현 |
| A13 | P0 | POST /api/terms/{id}/unsave | 404 | 독립 용어 저장 해제 없음 | A12와 대칭되는 해제. 학습 이력까지 지우지 않도록 분리 |
| A14 | P0 | POST /api/news/{id}/read | 200 | 경로 일치, 반복 요청으로 기록/통계 중복 | 읽음의 단위를 합의하고 유니크 키/멱등 처리·동시 요청 검증 |
| A15 | P1 | GET /api/news/search | 400 | 별도 검색 없음. search를 숫자 기사 ID로 해석하다 실패 | GET /api/news?keyword= 재사용 또는 고정 검색 경로 추가 |
| A16 | P0 | GET /api/me/saved-terms | 404 | GET /api/users/me/learned-terms는 학습 용어 목록 | 북마크/학습의 의미·페이지 구조 구분 |
| A17 | P0 | GET /api/me/saved-news | 404 | GET /api/users/me/bookmarks 조회 성공 | 경로 및 Spring Page→data 배열/meta 변환 |
| A18 | P0 | GET /api/me/read-history | 404 | 읽음 저장 테이블/Repository는 있지만 조회 API 없음 | 내 이력 DTO/정렬/페이지/중복 표시 정책 구현 |
| A19 | P1 | GET /api/me/activity | 404 | 날짜별 꿀 지급 집계 없음 | 지급 원장 기반 KST 날짜 집계, 빈 날짜·기간 제한 |
| A20 | P1 | GET /api/categories | 404 | 뉴스 카테고리 목록 API 없음 | 공통 코드/표시명/정렬/isNew 기준 확정 |
| A21 | P1 | GET /api/keywords | 404 | 뉴스 검색용 키워드 카드 API 없음 | 키워드 선정·이미지/제목·링크·갱신 기준 확정 |
| A22 | P2 | GET /api/me/insights | 404 | 관심/부족 분야 분석 없음 | 행동·퀴즈 기반 산출 기준 및 표본 부족 처리 |
| A23 | P2 | GET /api/search/trending | 404 | 실시간 트렌드 API 없음 | 기간/집계 이벤트/부정 요청 방지·캐시·빈 상태 |
| A24 | P2 | GET /api/search/suggestions | 404 | 금융 용어 자동완성과 뉴스 검색 자동완성은 다름 | 뉴스 대상 제안 범위·최소 길이·개수·정렬 합의 |
| A25 | P1 | PATCH /api/me/profile | 404 | PATCH /api/users/me는 JSON 닉네임만 지원·검증 | multipart 이미지 저장/파일 검증/URL 정책은 신규 구현 |
| A26 | P1 | GET /api/me/notification-settings | 404 | 설정 조회 API 없음. 알림 보관함과 다름 | 사용자별 기본값·허용 항목·저장 모델 확정 |
| A27 | P1 | PATCH /api/me/notification-settings | 404 | 설정 변경 API 없음 | 부분 갱신·유효성·소유권·발송 시 설정 반영 |
| A28 | P1 | DELETE /api/me | 404 | DELETE /api/auth/withdraw로 탈퇴·토큰 거부 검증 | 경로 통일, 보존/삭제 대상 안내 확정 |
| A29 | P2 | POST /api/me/devices | 404 | PUT /api/users/me/devices 등록·DB 저장 검증 | 메서드/경로·토큰 종류 통일. 등록 성공≠실제 푸시 성공 |

## 4. 공통 응답 계약 불일치

| 항목 | 노션 요청 | 현재 동작 / 확인 근거 |
|---|---|---|
| 인증 | A1/A2 외 Bearer 필수 | 뉴스 목록·상세는 익명 200도 확인. 정책/용어/카드도 공개 설정 |
| 성공 응답 | data, status | 현재 message 추가, null data는 생략 가능 |
| 목록 | data 배열 + meta | data가 Spring Page 객체이며 그 안에 content/last/size/number |
| 페이지 번호 | page=1이 첫 페이지 | page=0이 첫 페이지. page=1은 두 번째 페이지 |
| 페이지 크기 | limit=10 | 뉴스는 size 사용. limit은 무시되고 기본 20 |
| 다음 페이지 | meta.hasNext | meta 없음. Spring Page의 last 반대값으로 판정 가능 |
| ID | 모든 리소스 문자열 | JSON 숫자 Long. 앱 서비스의 String 변환은 서버 계약 일치가 아님 |
| 날짜 | ISO 8601 + 오프셋 | 뉴스 publishedAt이 LocalDateTime 직렬화되어 오프셋 없음 |
| 활동 일자 | KST 날짜 | 서버 LocalDate.now와 DB read_at::DATE 사용. 환경 시간대에 의존, 고정 KST 보장 없음 |
| 오류 | error 문자열 + HTTP와 같은 status | error 필드 없음. HTTP 401의 본문 status는 4001, HTTP 400은 4105 등 내부 코드 |
| 저장/읽음 멱등성 | 같은 요청은 같은 결과 | 명시적 뉴스 PUT/DELETE는 충족. read는 중복 기록. 퀴즈/용어 북마크는 아직 없음 |

실행에서 확인한 페이지 문제:

- 테스트 기사 하나가 일치하도록 keyword를 지정했다.
- page=1&limit=10 → data.number=1, data.size=20, content=[].
- page=0&size=10 → 해당 기사 1건 반환.

현재 형태의 **구조 예시**(실제 사용자 응답 덤프가 아님):

```json
{
  "status": 200,
  "message": "ok",
  "data": {
    "content": [{"id": 123, "title": "시험 기사"}],
    "number": 0,
    "size": 10,
    "last": true
  }
}
```

요청된 형태로 변환할 경우의 **제안 예시**:

```json
{
  "status": 200,
  "data": [{"id": "123", "title": "시험 기사"}],
  "meta": {"page": 1, "limit": 10, "hasNext": false}
}
```

## 5. 실제 결함과 수정 우선순위

### P0-1. 뉴스 홈 500

- 실패 요청: 인증된 GET /api/news, 검색어 미지정.
- 응답: HTTP 500. PostgreSQL: function lower(bytea) does not exist.
- 위치: src/main/java/com/youthletter/domain/news/repository/NewsArticleRepository.java의 search JPQL.
- 확인된 사실: null 검색어가 포함된 lower/concat SQL에서 타입 문제가 발생한다. 같은 테스트 클래스의 일부 다른 무필터 요청은 200이므로 모든 호출이 항상 실패한다고 일반화하지 않는다.
- 수정 방향: null 조건을 동적 쿼리에서 제외하거나 검색 파라미터 타입을 명시하는 방법을 검토한다. 원인 확정 시 바인딩/쿼리 재사용 조건도 점검한다.
- 재검증: 인증/익명, keyword 없음/빈 문자열/한글, category만, 날짜만, 조합, 연속 요청·호출 순서 변경.

### P0-2. 읽음 기록 중복

- 같은 사용자·기사로 POST read 두 번 → reading_history 2행.
- 통계는 countByUserId를 사용해 readNewsCount=2.
- 위치: domain/activity/service/ActivityService.java의 trackReading, domain/user/service/UserService.java의 getLearningStats.
- 결정 필요: 최초 읽음 1회인지, 하루 1회인지, 재독 이벤트는 별도 기록할지. 이력 이벤트를 유지하더라도 '읽은 기사 수'는 distinct article 기준인지 합의해야 한다.
- 같은 HTTP 응답이 반복되어도 DB 기록·포인트가 늘어나면 멱등성이 아니다.

### P0-3. 프론트 계약 불일치

동일 경로 /api/news에서 페이지 기본값을 전역으로 바꾸면 현재 연결한 앱에 영향이 있다. 앱 담당자와 아래 중 하나를 합의한다.

1. 현재 서버 계약을 기준으로 노션/프론트 수정.
2. 노션 계약용 호환 계층 또는 버전 API를 추가하고 기존 경로를 단계적으로 이전.

권고: 먼저 500·읽음 중복을 수정하고, URL/페이지/응답/ID 정책을 문서로 확정한 뒤 일괄 적용한다. 누락된 25개를 이름만 같은 빈 API로 추가해서는 안 된다.

### P0-4. 퀴즈·꿀·용어 저장 모델

현재 코드에 없으므로 신규 도메인 설계가 필요하다. 퀴즈 정답을 제출 전 응답에 노출하지 않는 것, 동일 답안 재전송 시 중복 지급하지 않는 것, 다른 사용자의 답안 조회 차단을 완료 기준에 포함한다.

문서의 'answer는 멱등'과 QUIZ_ALREADY_ANSWERED 409 예시는 해석 합의가 필요하다. 동일 요청 키의 재전송은 최초 결과를 재사용하고 다른 답안의 재제출은 409로 처리하는 방식 등을 선택할 수 있다. 아직 선택/구현한 정책이 아니다.

### P1. 시간·오류·프로필 업로드·알림 설정

- 저장 시간은 UTC를 유지하더라도 API 오프셋과 활동 집계 KST는 명시적으로 처리할 수 있다. DB 서버 시간대만 바꿔 해결하려 하면 안 된다.
- HTTP status와 내부 오류 code를 서로 다른 필드로 분리할지 합의한다.
- 이미지 업로드는 MIME/용량/확장자/저장 권한/삭제 수명/기존 이미지 보존까지 정의해야 한다.
- 앱 알림 설정과 iOS OS 알림 권한은 서로 다른 상태다.

## 6. 노션에 추가할 기존 API 문서

아래는 코드에서 존재를 확인했으나 노션 29개 목록에 별도 항목이 없는 API다. **이 표 전체를 이번에 런타임 검증했다는 뜻은 아니다.**

| 영역 | 추가할 문서 | 이번 검증 범위 |
|---|---|---|
| 이메일 인증 | POST /api/auth/signup, /login | 코드 확인. 기존 MobileAuthIntegrationTest가 별도 존재, 이번 감사 실행 대상은 아님 |
| 세션 | POST /api/auth/reissue, DELETE /api/auth/withdraw | 이번 감사에서 갱신/탈퇴 및 재접근 거부 확인 |
| 명시적 뉴스 저장 | GET/PUT/DELETE /api/news/{id}/bookmark | PUT/DELETE 반복과 저장 목록 재조회 확인 |
| 카드 조회 | GET /api/card-news, /{id}, /article/{articleId} | 코드 확인, 이번 실모델/카드 검증 미실행 |
| 뉴스 추천 | GET /api/news/today, /api/news/recommend | 코드 확인. A22 개인 학습 분석과 다른 기능 |
| 용어 사전 | GET /api/terms, /{id}, /search?query=, /autocomplete?query=; POST /{id}/learn | learn·학습 목록만 이번 실행 확인 |
| 내 정보 | GET/PATCH /api/users/me, /learning-stats, /bookmarks, /learned-terms | 이번 감사 실행 |
| 추천 조건 | PUT /api/users/me/categories, /policy-profile | 코드 확인. API 제공과 모바일 UI 연결은 별개 |
| 정책 | GET /api/policies, /{id}, /recommend; POST /{id}/bookmark | 코드 확인 |
| 소비 | /api/spending CRUD, /dashboard, /trend, /budget, /budget/status, /ai-insight | 코드 확인 |
| 알림 보관함 | GET /api/notifications; PATCH /{id}/read, /read-all | 코드 확인. A26/A27 알림 설정과 별개 |
| 기기 해제 | DELETE /api/users/me/devices/{deviceId} | 코드 확인 |
| 운영 전용 | /api/health, 관리자 AI 작업 접수/조회·상태, 크롤링 | 일반 앱 명세와 분리. 이번 관리자 실행 미실시 |

별도 보안 검토: 기존 웹 OAuth 성공 핸들러는 access/refresh 토큰을 리다이렉트 URL query에 넣는다. 이 흐름을 A1 모바일 인증 완성으로 간주하지 말고 안전한 코드 교환 방식 등을 검토해야 한다. 실제 제공자 로그인은 이번에 실행하지 않았다.

## 7. 추가 구현 전에 합의할 질문

1. 최종 API 계약은 노션 10/8 문서와 현재 앱 중 무엇을 기준으로 할까?
2. 공개 뉴스 열람을 유지할까, 노션처럼 로그인 필수로 바꿀까?
3. 오늘 학습 완료는 읽음/퀴즈 중 무엇이며 KST 자정 기준으로 확정할까?
4. 정답당 꿀 지급량, 오답 재도전, 같은 답안 재전송 규칙은?
5. 용어 저장과 학습 완료를 별도 상태로 관리할까?
6. 프로필 이미지 저장소/업로드 제한과 알림 설정 항목은?
7. OAuth 입력(code/idToken), 허용 제공자, 푸시 토큰 종류는?

질문 답변 전에도 홈 500 재현/수정과 영향 범위 점검은 진행할 수 있다. 나머지 정책을 개발자가 임의 확정하면 프론트와 다시 어긋날 수 있다.

## 8. 재현 테스트와 결과 해석

파일: src/test/java/com/youthletter/NotionApiAuditTest.java

```sh
cd /Users/da-eun/Desktop/youthletter-backend
docker start youthletter-postgres-test youthletter-redis-test
RUN_NOTION_API_AUDIT=true ./gradlew test --tests com.youthletter.NotionApiAuditTest --no-daemon
```

테스트는 29개 경로 감사 + 6개 계약/대체 기능 시나리오로 구성했다. DB/Redis 연결 주소는 이 테스트에 고정하며 원본 DB를 사용하지 않는다.

최종 실행 결과: **35개 중 34개 진단 assertion 통과, A4 정상 응답 assertion 1개 실패**, 종료 코드 1. A4의 500은 반복 실행에서도 재현됐다. 진단 코드 작성 중 용어 이력 테이블명 오타로 실패한 부분은 실제 테이블명 term_lookup_log로 수정 후 재실행했으며, 최종 남은 실패는 뉴스 목록 500이다. 모든 API가 정상이라는 의미의 34개 통과가 아니다.

- **감사 테스트의 통과 ≠ 노션 명세 합격.** 누락 경로가 실제로 미등록/오류인지, 계약 차이가 있는지를 확인하는 특성 테스트를 포함한다.
- A4는 정상 기대값 200을 유지해 실제 500에 대해 실패하도록 남겼다. 실패를 500 기대값으로 바꿔 초록색으로 만들지 않았다.
- 감사는 RUN_NOTION_API_AUDIT=true를 지정할 때만 실행된다. 평상시 CI에서 제외되는 진단용 테스트이며, 문제 해결 완료나 전체 테스트 통과를 뜻하지 않는다.
- 원본 JSON/토큰을 문서에 저장하지 않고 ID별 메서드/경로/HTTP 상태만 기록한다.
- HTML 결과: build/reports/tests/test/index.html. XML: build/test-results/test/TEST-com.youthletter.NotionApiAuditTest.xml. 다음 Gradle test가 덮어쓸 수 있으므로 본 문서에 핵심 결과를 보존한다.

이번 검증은 수정 전 상태를 기록한 것이다. 정책 확정·구현 후에는 명세 준수 assertion과 역할/소유권/동시성/오류 입력 테스트로 다시 검증해야 한다.
