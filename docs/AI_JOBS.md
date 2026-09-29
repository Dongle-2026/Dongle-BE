# 비동기 AI 작업 API

## 목적과 구조

관리자 생성 요청이 모델 응답을 수십 초 기다리지 않도록 DB에 작업을 접수하고 202를 반환한다. 기존 PostgreSQL을 사용하여 별도 메시지 브로커 운영 부담을 줄였다. 모델 호출 동안 DB 트랜잭션은 열지 않는다. 결과 저장과 성공 상태 변경은 동일 트랜잭션으로 처리한다.

기존 동기 API는 호환을 위해 유지한다. 신규 관리자 화면은 아래 큐 API를 사용해야 한다. 일반 앱 사용자는 생성 API 대신 저장된 뉴스/카드를 조회한다.

## 사전 적용

기존 DB는 백업 후 `src/main/resources/db/migration/V16__ai_jobs.sql`을 psql `-v ON_ERROR_STOP=1 -f`로 적용한다. 현재 Flyway 자동 실행은 없다. 테스트 DB에는 이번 개발에서 적용했다. 운영/개발 DB에 자동 적용하지 않았다.

`ai-jobs.enabled=false`로 작업자를 끌 수 있다. 기본값은 true. 테스트 프로필은 false. 실행 시 실제 모델 모드(`AI_USE_STUB=false`)가 필요하다. 스텁 작업은 실패로 끝나며 자동으로 재시도하지 않는다.

## API (ADMIN Bearer 토큰 필수)

`POST /api/admin/ai/jobs`

```json
{"articleId":123,"kind":"CARD"}
```

kind는 ARTICLE(요약·용어·임베딩) 또는 CARD(카드뉴스). 응답 HTTP/본문 status 모두 202, data에 id, articleId, kind, status, attempts, errorCode, createdAt, updatedAt를 포함한다. null 필드는 생략될 수 있다. 활성 작업이 있으면 동일 ID를 반환한다.

`GET /api/admin/ai/jobs/{id}`로 3~5초마다 조회한다. QUEUED/RUNNING 동안 대기 표시, SUCCEEDED이면 기존 기사/카드 상세 API를 새로 조회한다. FAILED이면 errorCode를 보여주고 관리자 재접수 버튼을 제공한다. 실패 후 POST를 다시 하면 새 작업 ID가 발급된다.

## 동시성·재시작 복구

- DB advisory transaction lock으로 claim을 직렬화하고 큐 전체에서 한 작업만 실행한다.
- 기사+종류별 활성 작업 unique index로 중복 접수를 방지한다.
- 작업마다 10분 lease와 임의 token을 부여한다. 서버가 중단되면 lease 만료 후 재접수된다. 즉시 복구가 아니며 최대 약 10분 기다린다.
- 이전 worker가 늦게 응답해도 현재 token·상태·lease가 맞지 않으면 결과를 저장하지 않는다.
- AI 서비스/타임아웃 장애는 30초 뒤 재시도, 최초 포함 최대 3회. 그 외 오류는 FAILED.
- 현재 AI 출력 검증 오류도 공통 AI_SERVICE_ERROR로 전달되어 최대 3회 재시도될 수 있다. 세분화된 재시도 정책은 후속 개선 대상이다.
- 기존 동기 생성·크롤러는 이 큐의 전역 직렬화 대상이 아니다. 동시에 호출하면 Node가 429를 반환할 수 있다. 관리자 운영에서는 큐 경로로 통일한다.
- 10분 lease는 기본 Spring 250초 호출 제한보다 길다. 호출 제한을 10분 이상으로 늘리면 안 된다. 긴 작업 지원 시 heartbeat lease 연장이 필요하다.

## 검증 범위

PostgreSQL 테스트로 중복 ID, 단일 claim, lease 만료 복구, 늦은 결과 거부, 제한 재시도, 카드 저장과 성공 상태를 검증한다. lease 만료는 DB 시간을 과거로 설정해 재현하며 실제 OS 프로세스 강제 종료 시험을 대체하지 않는다. 실제 모델+큐+앱 전체 시연은 별도로 수행해야 한다.
