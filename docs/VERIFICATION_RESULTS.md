# 검증 결과 기록

## 2026-09-29 추가 수정 검토

- 사용자 추가 수정 확인: `spring.jackson` 위치 복원, `FinancialTerm.path`의 ltree 캐스팅, 카드뉴스 스텁 생성 분기와 테스트, 가이드·체크리스트·본 문서.
- Git이 Xcode 사용권 미동의로 실행되지 않아 Git diff 대신 이전 작업 기록, 파일 수정 시각, 현재 내용과 테스트 XML을 대조했다. 파일 변경의 전체 범위나 작성자는 확정하지 않는다.
- 스텁 생성 복원은 유지했다. 다만 기존 실제 AI 카드를 스텁으로 교체할 수 있던 경로를 발견하여 Writer의 잠금·삭제 사이에서 차단했다(409). `prod`에서는 스텁 저장을 거부한다(403, `local`과 동시 활성화도 거부).
- `./gradlew test --rerun-tasks --no-daemon`: 54건 통과, 실모델 테스트 1건 조건부 생략, 실패 0건. 캐시 재사용이 아닌 전체 재실행이다.
- `cd ai-service && npm test`: 8건 통과.
- 추가 회귀 테스트: 운영 프로필 스텁 저장 차단, 실제 카드 덮어쓰기 거부 및 기존 데이터 보존.
- 실모델 별도 재실행 `RUN_AI_SMOKE=true AI_USE_STUB=false ./gradlew test --tests com.youthletter.LiveAiIntegrationTest --no-daemon`: **1건 실패**. 09:30:19 호출 시작 → 09:32:20 `/api/ai/process` HTTP 503, `LiveAiIntegrationTest.java:35`. Node의 기본 120초 제한과 일치하여 타임아웃 가능성이 높지만 현재 Spring 로그에는 세부 오류 코드가 없어 확정하지 않는다. `/health`에서는 두 모델 모두 사용 가능으로 응답했다. 이번 실행에서 카드 생성·DB 저장 단계까지 도달하지 않았다.
- 다음 우선 작업: 내부 오류 코드 관측 보강 → 장비 부하·모델 로딩을 분리한 지연 측정 → 생성 작업 큐 도입. 제한 시간을 무작정 늘리거나 검증을 끄는 방식으로 실패를 숨기지 않는다. 이전 성공 기록은 현재 안정성을 보증하지 않는다.

아래는 2026-09-28에 기록된 결과이며 위 재검증과 구분한다.

기준일: 2026-09-28. 이 문서의 수치는 아래 환경에서 직접 실행한 결과이며, 과거 문서의 실행 기록과 구분한다.

## 실행 환경

| 항목 | 값 |
|---|---|
| 장비 | 개발용 MacBook Air (Apple Silicon) |
| 테스트 DB | Docker `pgvector/pgvector:pg16` (5433, youthletter_test), Redis 7 (6380) |
| AI | Node v26 `ai-service/`, Ollama 로컬 `qwen3:8b`, `nomic-embed-text` |
| 입력 | 실제 뉴스가 아닌 연동 시험용 가상 기사 (`scripts/smoke.mjs`, `LiveAiIntegrationTest`) |

## 1. 자동 테스트

| 명령 | 결과 |
|---|---|
| `cd ai-service && npm test` | 8건 전체 통과 |
| `RUN_AI_SMOKE=true AI_USE_STUB=false ./gradlew test --no-daemon` | 53건 전체 통과 (실모델 연동 `LiveAiIntegrationTest` 포함, 57.7초) |

`RUN_AI_SMOKE`를 지정하지 않으면 `LiveAiIntegrationTest` 1건은 건너뛴다(52건 실행).

## 2. 실모델 스모크 (`node scripts/smoke.mjs`)

| 단계 | 처리 시간 | 결과 |
|---|---:|---|
| `/health` | 0.08초 | 두 모델 설치·응답 확인 |
| `/api/ai/process` | 97.5초 | 3문장 요약, 원문 등장 용어 6개, 768차원 벡터 (첫 호출, 모델 로딩 포함) |
| `/api/ai/card-news` | 85.4초 | 카드 5장, 검증 통과 |
| `/api/ai/spending-insight` | 9.5초 | 숫자 없는 제안 1문장 |

처리 시간은 모델 로딩 상태와 장비에 따라 크게 달라진다. 앱 요청 경로에서 실시간 카드 생성을 하지 않는 근거로 사용한다.

## 3. 이번 검증에서 발견·수정한 문제

| 문제 | 영향 | 수정 |
|---|---|---|
| `application.yml`에서 `jackson` 블록이 `ai-service:` 아래로 들어감 | `default-property-inclusion: non_null` 등 직렬화 설정이 적용되지 않음 | `spring.jackson` 위치로 복원 |
| `FinancialTerm.path`(ltree)에 varchar 바인딩 | AI가 사전에 없는 용어를 반환하면 기사 저장 전체가 실패 (기존에는 예외를 무시해 용어만 누락) | `@ColumnTransformer(write = "?::ltree")` |
| 카드 생성이 stub 모드에서도 Node 호출 | `AI_USE_STUB=true` 환경(Ollama 없는 팀원)에서 카드 생성 불가 | stub 모드에서는 `CardNewsStubGenerator` 결과를 같은 Writer로 저장, modelVersion=`stub` |

## 4. 검증하지 않은 범위

- 실제 한경 기사에 대한 사실 정확성: 고정 평가 기사 20건 사람 평가 필요
- 로컬 개발 DB(15432)에 `upgrade-capstone.sql` 적용: 팀원별 DB에서 각자 적용 후 확인
- FCM 실제 발송, 모바일 OAuth callback: 외부 콘솔 설정 필요
- 동시 요청 부하, 서버 재시작 시 생성 작업 복구: 영속 작업 큐 미구현
- Git diff·커밋: Xcode 사용권 동의 전에는 git 실행 불가
