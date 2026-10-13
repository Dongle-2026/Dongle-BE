# 프론트 연동 500 진단과 재검수

대상: `/api/news`, `/api/me/stats`, `/api/me/activity`, `/api/me/insights`, `/api/news/{articleId}/body`.
현재 로컬 재현 결과와 남은 상태는 [후속 작업표](FOLLOWUP_WORK_2026-10-13.md)를 따른다.

## 1. 먼저 같은 환경인지 확인

서버 base URL, 백엔드 커밋/이미지, DB 이름·포트, 요청 쿼리, 로그인 여부, 기사 ID, 발생 시각·요청 ID를 확인한다.
프론트의 500 응답 원문과 같은 시각의 서버 예외를 비교한다. 토큰·비밀번호·연결 비밀값은 문서나 로그 공유에서 제외한다.
18080은 별도 검증 서버이며 팀원이 호출하는 배포 서버와 같다고 가정하지 않는다.

## 2. 헬스와 스키마

새 코드의 `/api/health`와 `/actuator/health`는 기존 `status/database/redis`에 `schema`를 추가한다.
2026-10-13 2차 코드에서는 신고용 V27 content_reports 컬럼도 확인한다. 해당 버전 반영 전에 V27을 적용한다.

```json
{"status":200,"message":"ok","data":{"status":"UP","database":"connected","redis":"connected","schema":"ready"}}
```

DB·Redis·스키마 중 하나라도 준비되지 않으면 HTTP 503이다. 스키마는 `unavailable`로만 표시하고 공개 API에 내부 목록을 노출하지 않는다.
이 검사는 JPA validate에 잡히지 않는 JDBC 학습 테이블과 썸네일 컬럼의 조회 가능 여부를 보완한다.
RLS 정책·쓰기 권한·자료형 전체·데이터 품질까지 검사하는 것은 아니다. 기존 health 모니터링은 schema 누락 503을 반영한다.

API와 같은 DB 접속 계정으로 상세 점검:

```sh
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/schema-readiness.sql
```

결과 0행은 해당 검사 목록이 준비되었다는 뜻이다. `missing`은 테이블/컬럼 누락, `select_denied`는 해당 컬럼 조회 권한 부족이다.
환경변수에는 실제 API 계정의 접속 정보를 넣고 값을 출력하지 않는다. SQL은 읽기 전용이며 스키마를 고치지 않는다.
SQL 실행 자체가 권한 오류면 schema 접근 권한도 확인한다.

| 경로 | 확인할 주요 의존성 |
|---|---|
| `/api/news` | news_articles.thumbnail_url(V24), 요약 버전 중복, 로그인 시 news_bookmarks, limit 형식에서는 card_news/card_news_cards |
| `/api/me/stats` | reading_history, term_lookup_logs, honey_ledger·quiz_attempts(V23) |
| `/api/me/activity` | honey_ledger(V23) |
| `/api/me/insights` | quizzes·quiz_attempts(V23), reading_history, news_articles |
| `/api/news/{id}/body` | 기사·요약·용어, quizzes(V23), 로그인 시 term_bookmarks(V19), quiz_attempts/honey_ledger(V23), quiz_reveals(V25), 생성 필요 시 ai_jobs(V16/V23) |

누락을 확정한 뒤 백업·복원 리허설을 하고 필요한 SQL을 적용한다. [기존 DB 적용 순서](INTEGRATION_STATUS.md#기존-db-적용)를 참조한다.
기존 Docker 볼륨은 컨테이너 재시작만으로 init SQL을 재실행하지 않는다. `ddl-auto=update`도 JDBC 전용 테이블을 만들어 주지 않는다.

## 3. 같은 API를 한 번에 검사

```sh
python3 scripts/api_smoke.py --base-url http://127.0.0.1:18080
```

인증 검사는 본인 시험 계정의 유효한 access token을 `API_ACCESS_TOKEN` 환경변수로 제공한다. 토큰을 CLI 인자나 보고서에 넣지 않는다.
기사가 비어 있으면 `--article-id 123`처럼 실제 존재하는 ID를 지정한다.

도구는 health, limit/page와 size/page 두 목록 형식, 공개 본문, 토큰이 있으면 로그인 본문·통계·7일 활동·인사이트를 검사한다.
본문 API는 현재 서버 구현상 퀴즈가 없고 AI 작업자가 켜져 있으면 생성 작업을 접수할 수 있다. 조회만 시험하려면 격리 환경에서 `AI_JOBS_ENABLED=false`를 사용한다.
도구는 계정 생성·DB 초기화·데이터 삭제·푸시 발송을 하지 않는다.

- 종료 0: 모든 검사 PASS.
- 종료 1: HTTP 오류/연결 실패/예상 형식 불일치가 하나 이상 있음. httpStatus=0은 네트워크 계열 실패.
- 종료 2: 실패는 없으나 토큰 또는 기사 ID 부족으로 일부 SKIP. 전체 성공으로 처리하지 않는다.

결과에는 경로·HTTP 상태·판정만 출력하며 응답 원문·토큰은 출력하지 않는다. HTTP 리다이렉트는 따르지 않는다.
이전 서버처럼 health에 schema 필드가 없으면 health 검사는 FAIL이다. 새 진단 버전 반영 여부를 확인한다.

## 4. 브라우저 CORS 검수

기본 허용 origin은 localhost의 3000, **3001**, 8080이다. 별도 웹 주소는 `APP_CORS_ALLOWED_ORIGINS`를 쉼표로 구분해 설정한다.
이 값은 기본값에 추가되는 목록이 아니라 **전체 허용 목록을 교체**한다. origin에는 경로를 넣지 않는다.
운영 웹 주소와 로컬 주소는 필요한 것만 명시한다. 네이티브 앱 요청의 서버 500과 브라우저 CORS 차단을 혼동하지 않는다.

```sh
curl -i -X OPTIONS http://127.0.0.1:18080/api/me/stats \
  -H 'Origin: http://localhost:3001' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: authorization'
```

허용 주소는 Access-Control-Allow-Origin이 정확히 일치해야 하고, 미등록 주소에는 헤더를 반환하지 않는다.

## 5. 수정 완료 판정

원인 로그 → 수정 파일/설정 → 같은 요청의 성공 → 해당 회귀 테스트 → 앱 화면 재검수 → 작업표 결과 기록까지 연결한다.
현재 B01/B02는 진단·접근 설정 보완이다. 대상 서버에서 보고된 500이 해결되었다는 근거는 아직 없다.

2차 보완: 잘못된 뉴스 정렬 필드는 HTTP 400, 지원하지 않는 메서드는 HTTP 405(Allow 포함)로 반환한다.
격리 환경에서는 회원가입 토큰을 사용하는 실제 HTTP 스모크도 통과했다. [검증 범위](QUICK_IMPLEMENTATION_2026-10-13.md)를 참고한다.
