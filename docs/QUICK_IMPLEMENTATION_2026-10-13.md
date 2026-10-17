# 바로 진행 가능한 후속 개발 결과

2026-10-13. 원본의 기존 미커밋 변경을 보존한 `/private/tmp/youthletter-quick` 사본에서 구현·검수·테스트했다.
검증 DB/Redis는 새 Compose 프로젝트 `youthletter-quick`의 localhost 15434/16381이다. 기존 5433/6380, 15433/16380, 개발/운영 DB는 변경하지 않았다.

## 완료한 개발

| 항목 | 구현 결과 | 검증 |
|---|---|---|
| 퀴즈·카드 오류 신고 | 로그인 사용자 신고 접수, 동일 대상 재요청은 최초 신고 반환. 사용자별 독립. 관리자 상태 관리 | 권한·입력 검증·404·멱등성·처리 후 재신고·게시 상태/꿀 불변 통합 테스트 |
| HTTP 오류 구분 | 존재하지 않는 뉴스 정렬 필드 400, 지원하지 않는 메서드 405 + Allow 헤더 | 실제 Spring 요청 통합 테스트 |
| DB 초기화 누락 방지 | CI·테스트·프로덕션 새 DB 초기화에서 같은 V16 이상 SQL 탐색 스크립트 사용 | 새 격리 DB V27까지 초기화, 기존 스키마 재적용 성공 |
| SQL 실행 안전성 | 숫자 버전 순서, 공백 포함 경로, DO 내부 BEGIN과 실제 BEGIN; 구별. SQL 실패 시 중단 | 도구 테스트 2개 |
| 실제 HTTP API 검수 | 랜덤 포트의 실제 Tomcat 서버에서 회원가입 → 받은 토큰으로 Python 진단 실행 | health·뉴스 두 형식·공개/로그인 본문·stats·activity·insights 8개 판정 모두 PASS |
| 프로세스 장애 복구 | 별도 JVM이 실제 AiJobService로 claim 커밋 → 강제 종료 → 다른 JVM에서 만료 작업 복구·완료 | attempts=2, SUCCEEDED, 퀴즈 3개만 저장, 이전 claim·중복 완료 거부 |
| AI 20건 평가 준비 | 평가표 신규 생성, 결과/사람 검수/성공률/p50·p95/모델·프롬프트 버전 집계 | 미실행·미검수·실패·의미 오류·버전 혼합을 전체 합격으로 오인하지 않는 테스트 5개 |

### 신고 API 계약

| 메서드 / 경로 | 권한 | 요청·응답 |
|---|---|---|
| POST `/api/quizzes/{quizId}/reports` | 로그인 | `{reason, description?}` → `data:{id,status,createdAt}` |
| POST `/api/card-news/{cardNewsId}/reports` | 로그인 | 위와 같음. articleId가 아니라 카드뉴스 묶음 ID |
| GET `/api/admin/content-reports?status=OPEN&page=0&size=20` | ADMIN | Spring Page. status 생략 시 전체, 최신 생성시각·ID 역순 고정 |
| PATCH `/api/admin/content-reports/{reportId}` | ADMIN | `{status:"REVIEWED"}` → 관리자용 신고 상세 |

reason: `INCORRECT_CONTENT`, `AMBIGUOUS_ANSWER`, `OTHER`. description 선택, 최대 1000자.
status: `OPEN`, `REVIEWED`, `DISMISSED`. 재처리가 필요하면 관리자가 OPEN으로 되돌릴 수 있다.
동일 사용자의 같은 퀴즈/카드 신고는 최초 사유·내용·상태를 유지한다. 다른 사용자의 신고는 별도로 저장한다.
일반 사용자 응답에 신고자/다른 신고/관리자 상세는 포함하지 않는다. 관리자 상세에는 userId·대상 ID·사유·내용·상태·시각이 포함된다.
잘못된 body/enum은 400, 비로그인은 401, 관리자 기능 일반 사용자 접근은 403, 대상/신고 없음은 404.
신고 자체로 콘텐츠를 숨기거나 삭제하거나 꿀을 차감하지 않는다. 자동 숨김 기준·앱 버튼은 별도 작업이다.
존재하지 않는 신고의 내부 오류 코드: `4205 CONTENT_REPORT_NOT_FOUND`.

### DB 적용

추가 SQL은 `V27__content_reports.sql`이다. 사용자와 퀴즈/카드뉴스 FK, 대상별 사용자 유니크 인덱스, 상태 조회 인덱스를 포함한다.
readiness 검사에 content_reports 조회 컬럼을 추가했으므로 **새 서버 반영 전에 V27을 적용**한다.
기존 DB는 자동 변경되지 않는다. 백업·복원 리허설·적용 대상 확인 후 [적용 순서](INTEGRATION_STATUS.md#기존-db-적용)에 따라 처리한다.
프로덕션 Compose 수정은 **새 볼륨 초기화의 재현성 보완**이며 기존 운영 DB 마이그레이션/배포 완료를 의미하지 않는다.

공통 초기화 스크립트는 `CAPSTONE_SQL`, `DB_MIGRATIONS_DIR`로 호스트 SQL 위치를 지정할 수 있다.
Docker 기본 위치는 `/migrations/upgrade-capstone.sql`, `/migrations/db`다. CI와 테스트 Compose의 SQL 목록을 별도로 유지하지 않는다.
V12~V15의 효과는 기존 upgrade-capstone.sql이 담당하고, 추가 파일은 V16부터 버전 순으로 적용한다.
SQL에 명시적 `BEGIN;`이 없으면 파일 하나를 `psql -1`로 실행한다. 스크립트 전체가 단일 트랜잭션인 것은 아니다.

### 회귀·프로세스 복구 재현

```sh
docker compose -p youthletter-quick -f docker-compose.quick-test.yml up -d --wait
RUN_JOB_RESTART_TEST=true DB_PORT=15434 REDIS_PORT=16381 ./gradlew test bootJar --no-daemon
python3 -m unittest discover -s scripts/tests -v
cd ai-service
npm test
```

재시작 시험은 명시적 opt-in이다. 기존 작업이 QUEUED/RUNNING이면 시작을 거부한다. 격리 DB에서만 실행한다.
실제 AiJobService·QuizWriter·PostgreSQL을 두 하위 JVM에서 호출한다. ARTICLE/CARD 저장기는 이 시험에서 사용하지 않는다.
10분을 기다리는 대신 **이 시험 작업의 lease만** 만료시킨다. 복구 결과는 직접 작성한 시험 퀴즈로 저장하고 종료 시 시험 기사/작업을 삭제한다.
이는 프로세스 경계를 넘는 claim/lease/원자적 퀴즈 저장 검증이다. 실제 Ollama 모델 호출 중 Spring 애플리케이션 전체 종료 시험은 별도로 남아 있다.
HTTP 시험도 자체 생성한 사용자·기사·refresh 키만 정리한다. 토큰·응답 원문은 결과 보고서에 저장하지 않는다.

### AI 평가 도구

```sh
python3 scripts/ai_evaluation.py --init /private/tmp/ai-evaluation.json
python3 scripts/ai_evaluation.py --input /private/tmp/ai-evaluation.json
```

첫 명령은 20개 case, 각 SUMMARY/CARD/QUIZ의 **미실행 평가표**를 만든다. 기존 파일을 덮어쓰지 않는다.
원문/모델 결과를 대조한 뒤 articleId, success, durationMs, modelVersion, promptVersion과 review를 채운다.
review의 facts(사실), numbers(숫자), unambiguous(의미·보기 명확성)는 사람이 판단해 true/false로 기록한다. 미검수는 null로 둔다.
실행된 case는 서로 다른 articleId가 필요하다. 종류별 모델·프롬프트 버전 혼합은 versionsConsistent=false로 표시하고 allPassed를 false로 둔다.
성공률은 성공/시도, 품질 합격률은 모든 검수 기준 통과/검수 완료 성공 결과다. 미검수 성공 수는 별도 표시한다.
p50/p95는 성공·실패 시도의 지연을 함께 포함한 nearest-rank 방식이다. 실패를 제외해 지연을 낮게 보이지 않는다.
완성된 20건에서 전부 실행·검수하고 생성/품질 실패가 없을 때만 exit 0, 미완료/실패/혼합 버전은 exit 2, 입력 오류는 argparse 오류다.
이 도구는 모델을 호출하거나 사실성을 자동 판정하지 않는다. **실제 기사 20건 평가는 아직 수행하지 않았다.**

## 최종 검증 결과

| 검사 | 결과 |
|---|---|
| 백엔드 전체 + 프로세스 복구 + 실제 HTTP | 161개 중 **152 통과, 실패/오류 0, 생략 9** |
| 생략 사유 | 실제 모델 전용 LiveAiIntegrationTest 2개, 과거 GAP 특성 확인용 NotionApiAuditTest 7개 |
| AI 서비스 | 21개 통과 |
| Python 도구 | 10개 통과 |
| 실행용 JAR | bootJar 성공 |
| Compose/셸 구문 | quick-test/test/local 설정 검증, 초기화 스크립트 sh -n 통과 |

초기 검증에서 신고 시험 fixture의 필수 category 누락과 재시작 시험의 AssertJ 제네릭 추론 오류가 발견되어 수정했고,
수정 후 위 전체 테스트를 다시 실행해 통과했다. 미실행 평가표의 집계는 complete=false/allPassed=false/exit 2로 확인했다.
기계 판독 결과: [검증 증거](verification/quick-2026-10-13.json).

## 계속 남아 있는 항목

실제 프론트 오류 서버의 주소/로그에 따른 500 재현, 대상 DB V27 반영과 배포, 앱 신고 버튼·화면 연결,
실계정 소셜 로그인·푸시 수신, 실제 AI 기사 20건 평가, 모델 포함 전체 서버 강제 종료 시험,
정책/주가 공급원·모델 교체·운영 주소/제출 빌드는 외부 설정/환경 또는 정책 결정이 필요하다.
현재 실행 중인 18080 컨테이너를 교체하지 않았으며, 원래 보고된 500을 해결 완료로 표시하지 않는다.
