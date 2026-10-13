# 마무리 개발·검증 (2026-10-13)

## 이번 변경

- 실제 원문에는 `article:published_time`이 있는데 수집기가 `LocalDateTime.now()`를 발행일로 저장하던 오류를 수정했다.
- 발행 메타데이터 → `datePublished` 메타/시간 요소 순으로 검사한다. 시간대가 있는 ISO 시각을 기존 JDBC 서버 시간대로 변환한다.
- 발행 시각을 알 수 없으면 null이다. 수정 시각·수집 시각으로 발행 시각을 지어내지 않는다. 기존 `crawled_at`은 계속 수집 시각이다.
- 과거 저장된 발행일은 이번 변경으로 소급 수정하지 않는다. JSON-LD만 제공하는 사이트는 현재 날짜 추출 범위 밖이다.
- 앞서 추가한 크롤링 실패 처리(목록 요청 실패/신규 기사 전부 실패를 정상 0건으로 오인하지 않음)와 테스트를 함께 검증한다.
- 샘플·실모델 시험 DB와 회귀 시험 DB를 분리하는 `docker-compose.regression.yml`을 추가했다.

## 왜 시험 DB를 분리했는가

기존 quick-test DB에 기사 37건이 남아 있었다. 키워드 상위 20개 검사에서 시험용 키워드가
목록 밖으로 밀려 한 테스트가 실패했다. 이 데이터를 삭제하거나 테스트 기대값을 낮추지 않고,
새 Compose 프로젝트/볼륨을 사용해 빈 데이터셋에서 회귀 검사한다.
기존 개발·검증·샘플 DB는 보존한다. 회귀 DB에는 수동 샘플 SQL이나 실제 기사 배치를 넣지 않는다.

```sh
docker compose -p youthletter-regression -f docker-compose.regression.yml up -d --wait
DB_PORT=15435 REDIS_PORT=16382 ./gradlew test bootJar --no-daemon --rerun-tasks
python3 -m unittest discover -s scripts/tests -v
cd ai-service
npm ci
npm test
```

종료 시 `docker compose -p youthletter-regression -f docker-compose.regression.yml stop`을 사용한다.
기존 DB 볼륨 삭제나 초기화는 필요하지 않다. 이미 사용된 회귀 볼륨도 초기화 SQL을 자동 재실행하지 않는다.

## 검증 범위와 남은 출시 조건

자동 테스트 통과와 실제 AI 내용 품질·휴대폰 동작·배포 완료는 다른 기준이다.
기존 18080 서버는 이번 작업에서 교체하지 않는다. 신규 빌드의 운영 적용 전에는 DB 스키마 준비 상태,
백업·복원, 프론트와 동일한 URL/계정의 API 검수가 필요하다.

남은 외부 조건은 실기기 OAuth 제공자 설정, 푸시 권한/개발 빌드, HTTPS 배포 주소,
실제 정책·주가 공급원 확정이다. 실제 AI 기사 20건의 사실·숫자·보기 명확성 검수는
소수 기사 생성 성공만으로 완료 처리하지 않는다.

프로젝트는 `/Users/da-eun/Desktop/데스크탑 - 젤큰은/youthletter-backend`로 이동했다.
기존 채팅의 `/Users/da-eun/Desktop/youthletter-backend`에는 소스가 없다.
이동된 폴더의 빌드/의존성 파일 읽기 지연 때문에 검증은 동일 소스를 복사한
`/private/tmp/youthletter-final-0AoMmx`에서 수행하고 Node 의존성은 lockfile로 새로 설치했다.
검증용 사본에는 비밀 환경 파일을 복사하지 않았다.

## 재검증 결과

| 검사 | 결과 |
|---|---|
| 새 회귀 DB의 전체 백엔드 | 168개 중 158 통과, 실패/오류 0, 조건부 생략 10 |
| 생략 구분 | 실모델 2, 과거 Notion GAP 검사 7, 선택적 프로세스 복구 1 |
| 실행 JAR | `bootJar` 성공 |
| AI 서비스 자동 검사 | 21 통과 (모델 응답을 제어하는 자동 테스트; 실모델 품질 검사는 별도) |
| Python 도구 | 13 통과 |
| 원문 메타데이터 | 한경 기사 `202610093056i`에서 `2026-10-11T20:40:03+09:00` 발행 시각 확인 |

현재 작업 폴더에는 다른 작업에서 만든 미커밋 코드·문서와 삭제 사항이 여전히 있다.
이번 커밋은 수집 시각 수정 및 회귀 환경 관련 파일만 포함하며, 다른 작업을 임의로 일괄 커밋하지 않는다.
전체 테스트는 그 기존 변경을 포함한 현재 소스 사본 기준이다. 이 커밋 하나만으로 전체 미커밋 기능이 배포되는 것은 아니다.
