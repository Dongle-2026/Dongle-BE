# 직접 실행·테스트·배포하기

기준: 2026-10-13. 모든 명령은 **이 저장소 루트**에서 실행한다. macOS/Linux의 bash/zsh 기준이며 Windows는 WSL에서 실행한다. 아래 `sandbox`는 개발용이며 운영에 사용하지 않는다.

## 1. 필요한 프로그램과 경로

Docker Desktop(또는 Linux Docker Engine + Compose), JDK 17, Python 3가 필요하다. AI 서비스를 직접 실행할 때 Node.js 22 이상과 Ollama가 추가로 필요하다.

```sh
pwd
ls gradlew docker-compose.quick-test.yml
java -version
docker version
docker compose version
python3 --version
```

`gradlew`가 없으면 다른 폴더에 들어간 것이다. 실제 `build.gradle`, `src`, `docker`가 있는 폴더를 IDE에서 열거나 터미널에서 이동한다. 경로에 공백이 있으면 `cd "경로"`처럼 따옴표로 감싼다. `docker version`의 Server가 실패하면 Docker Desktop을 먼저 실행한다. Gradle wrapper를 사용하므로 Gradle 별도 설치는 필요 없다.

| 용도 | 호스트 주소 | DB/계정 |
|---|---|---|
| 이번 안내의 격리 PostgreSQL | 127.0.0.1:15434 | youthletter_test / test / test1234 |
| 격리 Redis | 127.0.0.1:16381 | 비밀번호 없음, 로컬 한정 |
| 직접 실행하는 API | http://localhost:18081 | local,sandbox 프로필 |
| 기존 verify Docker API | http://localhost:18080 | 기존 환경, 이 안내에서 변경 안 함 |
| 운영 Compose API | 서버:8080 | .env.deploy로 설정 |

## 2. DB와 Redis 켜기

```sh
docker compose -p youthletter-quick -f docker-compose.quick-test.yml up -d --wait
docker compose -p youthletter-quick -f docker-compose.quick-test.yml ps
```

처음 만들면 init.sql → upgrade-capstone.sql → V16~V27 순서로 스키마를 만든다. `healthy`가 될 때까지 기다린다. 기존 볼륨은 시작 스크립트가 다시 실행되지 않는다.

```sh
docker compose -p youthletter-quick -f docker-compose.quick-test.yml logs --tail=100 postgres
docker compose -p youthletter-quick -f docker-compose.quick-test.yml exec -T postgres psql -U test -d youthletter_test -c "SELECT to_regclass('content_reports');"
docker compose -p youthletter-quick -f docker-compose.quick-test.yml exec -T redis redis-cli ping
```

각각 `content_reports`, `PONG`이 나와야 한다. 이 격리 DB에 업데이트를 적용하려면 백업 후 다음을 실행한다. 운영 DB는 9절 절차를 따른다.

```sh
docker compose -p youthletter-quick -f docker-compose.quick-test.yml exec -T postgres sh /docker-entrypoint-initdb.d/02-apply-migrations.sh
```

샘플 뉴스가 필요하면 **이 격리 DB에서만** 넣는다. 테스트 종료 후 서버를 켜고 실행한다.

```sh
docker compose -p youthletter-quick -f docker-compose.quick-test.yml exec -T postgres psql -U test -d youthletter_test -v ON_ERROR_STOP=1 < docker/postgres/03-sample-data.sql
```

## 3. 서버 켜기·끄기

터미널 A에서:

```sh
./gradlew bootRun --args='--spring.profiles.active=local,sandbox'
```

이 프로필은 15434/16381에 연결하고 18081에서 API를 실행한다. DB 자동 구조 변경은 끄고(validate), AI stub을 사용하며 AI 작업자·크롤러·정책 알림·푸시 스케줄을 끈다. stub 상태는 실제 AI 품질 검증 결과가 아니다.

`Started YouthLetterApplication` 로그 이후 터미널 B에서:

```sh
curl -i http://localhost:18081/api/health
curl -sS 'http://localhost:18081/api/news?page=1&limit=10'
```

health는 HTTP 200, `data.status=UP`, `data.schema=ready`여야 한다. Swagger: http://localhost:18081/swagger-ui.html

JAR로 켜려면:

```sh
./gradlew bootJar --no-daemon
java -jar build/libs/youthletter-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local,sandbox
```

bootRun과 JAR를 동시에 같은 포트에서 실행하지 않는다. 서버 종료는 해당 터미널 `Ctrl+C`. DB 종료·재개는 아래와 같고 데이터는 유지된다.

```sh
docker compose -p youthletter-quick -f docker-compose.quick-test.yml stop
docker compose -p youthletter-quick -f docker-compose.quick-test.yml start
```

`down -v`는 데이터를 지우므로 일반 종료에 사용하지 않는다.

## 4. 회원가입·로그인과 최초 500 제보 API 확인

Swagger의 Auth에서 회원가입하거나 다음 명령을 실행한다. 예시는 로컬 시험 계정이다. 이미 가입했으면 login만 실행한다.

```sh
curl -sS http://localhost:18081/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"manual@test.invalid","password":"LocalTest1234!","name":"수동테스트","nickname":"수동검수"}'
```

가입은 201, 로그인은 200이며 `data.accessToken`, `data.refreshToken`이 반환된다. 토큰이 보이는 화면은 공유하지 않는다. 아래 명령은 로그인 결과에서 accessToken만 메모리에 저장한다.

```sh
export API_ACCESS_TOKEN="$(curl -fsS http://localhost:18081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"manual@test.invalid","password":"LocalTest1234!"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')"
python3 scripts/api_smoke.py --base-url http://localhost:18081
```

health, 뉴스 두 형식, 공개·로그인 본문, stats/activity/insights의 **8개 PASS**가 목표다. 뉴스가 없으면 본문은 SKIP이므로 샘플 적재 후 다시 실행한다. 반환 코드 0=전체 통과, 1=실패, 2=미실행 있음. ID를 고정하려면 `--article-id 실제ID`를 붙인다. accessToken 만료(15분)로 401이면 다시 로그인한다.

직접 확인할 수도 있다.

```sh
curl -sS http://localhost:18081/api/me/stats -H "Authorization: Bearer $API_ACCESS_TOKEN"
curl -sS 'http://localhost:18081/api/me/activity?days=7' -H "Authorization: Bearer $API_ACCESS_TOKEN"
curl -sS http://localhost:18081/api/me/insights -H "Authorization: Bearer $API_ACCESS_TOKEN"
curl -sS http://localhost:18081/api/news/1/body
```

마지막 `1`은 뉴스 목록에 실제 존재하는 ID로 바꾼다. 404는 해당 ID가 없는 경우이며 500과 구분한다. 뉴스 목록은 `limit` 사용 시 앱용 배열(page=1), `size` 사용 시 Spring Page(page=0)로 응답한다.

## 5. 추가된 신고 API 직접 검수

Swagger 또는 본문 응답에서 실제 quizId를 확인한다. 카드 신고는 articleId가 아니라 cardNewsId다.

```sh
QUIZ_ID=실제퀴즈숫자ID
curl -sS "http://localhost:18081/api/quizzes/$QUIZ_ID/reports" \
  -H "Authorization: Bearer $API_ACCESS_TOKEN" -H 'Content-Type: application/json' \
  -d '{"reason":"INCORRECT_CONTENT","description":"원문과 정답 설명을 비교해주세요"}'
curl -sS 'http://localhost:18081/api/me/content-reports?status=OPEN&page=0&size=20' \
  -H "Authorization: Bearer $API_ACCESS_TOKEN"
```

`GET /api/me/content-reports`는 이번 추가 API다. `status` 생략 시 본인 신고 전체, `OPEN/REVIEWED/DISMISSED` 필터 지원, 최신순 고정. 응답은 Page이며 id, quizId 또는 cardNewsId, reason, description, status, createdAt, updatedAt을 포함한다. 다른 사용자의 신고는 반환하지 않는다. 존재하지 않는 null 필드는 생략될 수 있다.

같은 신고를 두 번 전송해도 ID가 같고 최초 내용이 유지되어야 한다. reason은 INCORRECT_CONTENT/AMBIGUOUS_ANSWER/OTHER, 설명 최대 1000자다.

관리자 시험은 **격리 DB 시험 계정만** 승격한다.

```sh
docker compose -p youthletter-quick -f docker-compose.quick-test.yml exec -T postgres psql -U test -d youthletter_test \
  -c "UPDATE users SET role='ADMIN' WHERE email='manual@test.invalid';"
```

4절 login을 다시 실행해 새 토큰을 받는다. `GET /api/admin/content-reports`, `PATCH /api/admin/content-reports/{id}`에 `{"status":"REVIEWED"}` 전송 후 내 신고에서도 REVIEWED가 보이는지 확인한다. 일반 사용자 토큰은 관리자 요청 시 403이어야 한다. 처리는 콘텐츠 자동 숨김이나 꿀 변경을 하지 않는다. 테스트 후 같은 SQL로 role='USER'로 되돌리고 재로그인한다.

## 6. 자동 테스트

수동 서버는 먼저 종료한다. 테스트가 전용 DB에 시험 데이터를 쓰므로 운영 DB 연결값을 사용하지 않는다.

```sh
DB_PORT=15434 REDIS_PORT=16381 ./gradlew test bootJar --no-daemon
python3 -m unittest discover -s scripts/tests -v
(cd ai-service && npm ci && npm test)
```

백엔드 HTML 결과: `build/reports/tests/test/index.html`. 기본 제외 항목은 실모델·과거 감사·하위 JVM 복구 조건부 테스트다. 복구 시험을 포함하려면:

```sh
RUN_JOB_RESTART_TEST=true DB_PORT=15434 REDIS_PORT=16381 ./gradlew test --no-daemon
```

동시에 여러 Gradle 테스트를 실행하지 않는다. PostgreSQL 연결 한도 때문에 실패할 수 있다. 로컬 HTTP 통합 시험은 임의의 빈 포트를 사용하고 실제 회원가입 토큰으로 Python 진단을 실행한다.

## 7. 실제 AI 연결

Ollama에 필요한 모델을 준비한 후 AI 서비스를 실행한다. 이미 실행 중인 Ollama가 있으면 중복 실행하지 않는다.

```sh
ollama list
ollama pull qwen3:8b
ollama pull nomic-embed-text
cd ai-service
npm ci
cp .env.example .env
npm start
```

기본 주소는 127.0.0.1:3000이다. 다른 터미널에서 `curl -i http://127.0.0.1:3000/health`로 모델 연결을 확인한다. AI에 토큰을 설정했으면 `X-AI-Service-Token` 헤더도 필요하다. 루트로 돌아와 API를 재시작한다.

```sh
AI_USE_STUB=false AI_JOBS_ENABLED=true AI_SERVICE_BASE_URL=http://127.0.0.1:3000 \
  ./gradlew bootRun --args='--spring.profiles.active=local,sandbox'
```

AI_SERVICE_TOKEN은 설정했다면 두 서비스에 동일하게 전달한다. AI 생성은 오래 걸릴 수 있으며 뉴스 본문의 GENERATING/READY/UNAVAILABLE 및 작업 상태를 확인한다. API health 성공만으로 모델 성공을 판단하지 않는다. 20건 평가는 `python3 scripts/ai_evaluation.py --init /tmp/ai-review.json`으로 표를 만든 후 실제 생성 결과·시간·원문 대조 결과를 입력하고 `--input /tmp/ai-review.json`으로 집계한다. 도구가 생성·사실 검수를 대신하지 않는다.

## 8. DB 백업과 복원 리허설

```sh
mkdir -p backups
python3 scripts/db_archive.py backup backups/manual-20261013.dump
python3 scripts/db_archive.py restore backups/manual-20261013.dump --new-database restore_rehearsal_20261013
```

백업은 PostgreSQL custom archive, 파일 권한 600이며 같은 파일을 덮어쓰지 않는다. 복원은 **없는 새 DB만 생성**하고 한 트랜잭션으로 적재한다. 기존 DB명은 거부한다. 실패한 새 DB는 검사용으로 남으므로 오류를 고친 뒤 새 이름으로 재시험한다. 역할·권한은 원본과 별도이며 복원은 소유권/ACL을 가져오지 않는다. 실제 API 계정 권한·readiness는 추가 검증해야 한다. Redis는 포함하지 않으므로 복구 후 다시 로그인할 수 있다.

```sh
docker compose -p youthletter-quick -f docker-compose.quick-test.yml exec -T postgres psql -U test -d restore_rehearsal_20261013 \
  -c "SELECT (SELECT count(*) FROM users) users, (SELECT count(*) FROM news_articles) articles, (SELECT count(*) FROM content_reports) reports;"
```

원본 DB에서도 같은 SQL을 실행해 개수를 비교한다. 백업 파일은 개인정보를 포함할 수 있으므로 접근을 제한한 별도 저장소에도 보관한다.

## 9. 서버 배포

현재 제공 범위는 한 호스트의 Docker Compose다. 운영 서버·도메인은 확정되지 않았으며 아래 절차가 실제 배포 완료를 의미하지 않는다. 프론트와 Ollama는 별도 준비한다. 저장소 전체가 필요하고 JAR만 복사하면 SQL/Compose/AI 파일이 빠진다.

### 9-1. 설정과 빌드

서버에서 검증한 버전의 저장소를 준비하고:

```sh
cp .env.deploy.example .env.deploy
chmod 600 .env.deploy
```

편집기로 DB_PASSWORD, JWT_SECRET, AI_SERVICE_TOKEN, 도메인과 CORS를 실제 값으로 바꾼다. JWT_SECRET은 충분히 긴 무작위 값(예: 64자 이상의 hex 문자열), AI 토큰도 무작위 값으로 설정한다. `CHANGE_ME`를 남기지 않는다. `openssl rand -hex 32`로 각각 별도 생성할 수 있다. `.env.deploy`와 백업은 Git 제외 대상이다.

OAuth 미사용이면 placeholder로 기본 이메일 로그인을 시험할 수 있다. 소셜 로그인에는 제공자 콘솔 값·redirect URI·모바일 audiences가 필요하다. 푸시는 기기 시험 전까지 false를 유지한다.

```sh
docker compose --env-file .env.deploy -p youthletter-prod -f docker-compose.prod.yml --profile ai config --quiet
docker compose --env-file .env.deploy -p youthletter-prod -f docker-compose.prod.yml --profile ai build
```

`config`의 전체 출력은 비밀값을 포함할 수 있어 `--quiet`를 사용한다. AI 컨테이너는 포트를 외부 공개하지 않고 backend와 내부 네트워크로 통신한다. Ollama 주소는 **컨테이너에서 접근 가능한** OLLAMA_BASE_URL로 설정한다. Linux의 호스트 Ollama가 loopback에만 바인딩되면 host-gateway에서 접근할 수 없으므로 모델 서버의 바인딩/방화벽을 환경에 맞게 설정한다. 공개 인터넷에 Ollama 포트를 노출하지 않는다.

### 9-2. 신규 DB의 첫 실행

```sh
docker compose --env-file .env.deploy -p youthletter-prod -f docker-compose.prod.yml up -d --wait postgres redis
docker compose --env-file .env.deploy -p youthletter-prod -f docker-compose.prod.yml --profile ai up -d --wait --wait-timeout 180
curl -i http://127.0.0.1:8080/api/health
```

운영은 샘플 데이터를 넣지 않는다. 빈 목록은 정상이다. AI 없는 기본 API 시연은 .env.deploy에서 AI_USE_STUB=true, AI_JOBS_ENABLED=false로 설정하고 `--profile ai` 없이 실행한다. 이때 실제 AI 연동 완료로 표시하지 않는다.

### 9-3. 기존 DB 업데이트

기존 운영 Compose는 고정 컨테이너/볼륨 이름을 사용하므로 같은 호스트에 프로젝트 이름만 바꿔 두 벌 실행하면 안 된다. 기존 서버의 Compose·.env·DB 대상을 확인하고 점검 시간을 잡는다.

1. API 쓰기를 멈춘다: 아래 backend stop.
2. 백업하고 새 DB 복원 리허설(8절) 및 개수·권한 검증을 한다.
3. 공통 SQL을 적용한다. 새 볼륨 초기화가 아닌 명시적 실행이 필요하다.
4. 검증 버전 이미지를 띄우고 health·인증 API·CORS를 점검한다.

```sh
docker compose --env-file .env.deploy -p youthletter-prod -f docker-compose.prod.yml stop backend
python3 scripts/db_archive.py --compose-file docker-compose.prod.yml --project youthletter-prod --env-file .env.deploy backup backups/pre-release.dump
python3 scripts/db_archive.py --compose-file docker-compose.prod.yml --project youthletter-prod --env-file .env.deploy restore backups/pre-release.dump --new-database release_rehearsal
docker compose --env-file .env.deploy -p youthletter-prod -f docker-compose.prod.yml exec -T postgres sh /docker-entrypoint-initdb.d/02-apply-migrations.sh
docker compose --env-file .env.deploy -p youthletter-prod -f docker-compose.prod.yml --profile ai up -d --build --wait --wait-timeout 180
```

위 단계는 **각 명령 성공과 복원 검증 후 다음 명령**을 진행한다. migration은 파일 단위 트랜잭션이며 전체 묶음이 한 번에 rollback되는 것은 아니다. 원본 V27 이전의 스키마 누락도 확인해야 한다. 운영은 ddl-auto=validate이며 애플리케이션이 빠진 테이블을 자동 생성하지 않는다.

### 9-4. HTTPS·프론트 연결·배포 검수

도메인을 서버에 연결하고 Nginx/Caddy/클라우드 로드밸런서에서 TLS를 종료하여 API 8080으로 프록시한다. 인프라별 인증서 설정은 운영 환경 확정 후 적용한다. 외부 공개는 HTTPS를 사용하고 DB/Redis 포트는 loopback 바인딩을 유지한다. 호스트 방화벽에서 8080은 필요한 프록시 경로만 허용한다.

프론트 API URL은 `https://API도메인`이며 `http://backend:8080`은 브라우저에서 사용할 수 없다. APP_CORS_ALLOWED_ORIGINS에는 브라우저의 실제 origin(스킴·호스트·포트, 경로 없음)을 쉼표로 넣는다. 모바일 실기기에서 localhost는 폰 자신이므로 컴퓨터 LAN 주소 또는 배포 URL을 사용한다.

```sh
curl -i -X OPTIONS 'https://API도메인/api/me/stats' \
  -H 'Origin: https://프론트도메인' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: authorization'
python3 scripts/api_smoke.py --base-url https://API도메인 --article-id 실제기사ID
docker compose --env-file .env.deploy -p youthletter-prod -f docker-compose.prod.yml logs --tail=100 backend ai-service
```

배포 서버에서 발급한 API_ACCESS_TOKEN으로 검사한다. 로컬 토큰은 다른 서버에서 사용할 수 없다. health 200 + schema ready, 8 PASS, 허용 Origin 응답, 일반 사용자 관리자 접근 403을 확인한다.

### 9-4-1. 수집한 콘텐츠를 운영 DB에 넣기

로컬 `./run-pipeline`으로 모은 기사·요약·용어·카드뉴스·퀴즈를 운영 DB로 옮긴다. 운영 서버가 수집을 시작하기 전에 초기 데이터를 채우는 용도다.

1. 운영 DB가 9-2/9-3 절차로 V28까지 적용돼 있어야 한다(`content_pipeline_runs` 테이블 존재).
2. 콘텐츠를 옮기기 전 운영 DB를 백업한다(9-3의 `db_archive.py backup`).
3. 파이프라인 DB와 운영 DB가 **같은 호스트**에서 Docker로 떠 있으면 바로 옮긴다. 운영 컨테이너 이름은 `youthletter-postgres`다.

```sh
python3 scripts/import_content.py --target-container youthletter-postgres --target-db "$DB_NAME" --target-user "$DB_USERNAME" --dry-run
python3 scripts/import_content.py --target-container youthletter-postgres --target-db "$DB_NAME" --target-user "$DB_USERNAME"
```

4. 운영 서버가 **다른 컴퓨터**라면 파이프라인 DB를 파일로 뽑아 서버로 복사한 뒤, 서버에서 임시 DB로 복원하고 그 DB를 원본으로 지정한다.

```sh
# 로컬(파이프라인 DB 백업)
python3 scripts/db_archive.py --compose-file docker-compose.pipeline.yml --project youthletter-pipeline --env-file .env.pipeline.local backup backups/pipeline-content.dump
scp backups/pipeline-content.dump 서버:/경로/youthletter-backend/backups/
# 서버(임시 DB로 복원 → 운영 DB로 옮기기)
python3 scripts/db_archive.py --compose-file docker-compose.prod.yml --project youthletter-prod --env-file .env.deploy restore backups/pipeline-content.dump --new-database pipeline_import
python3 scripts/import_content.py --source-container youthletter-postgres --source-db pipeline_import --source-user "$DB_USERNAME" \
  --target-container youthletter-postgres --target-db "$DB_NAME" --target-user "$DB_USERNAME" --dry-run
```

dry-run의 `imported_articles` 개수를 확인한 뒤 `--dry-run` 없이 다시 실행한다. 같은 명령을 다시 실행해도 이미 옮긴 기사는 건너뛴다. 확인 후 임시 DB(`pipeline_import`)는 지워도 된다.
`/api/news`, 기능 확인 페이지(`https://API도메인/`)에서 기사·카드뉴스·퀴즈가 보이면 완료다.

### 9-5. 실패 시 복구

이전 Git 커밋/이미지 태그와 배포 설정을 릴리스별 보관한다. 앱만 실패하고 스키마가 이전 앱과 호환되면 이전 이미지를 다시 띄운다. DB 문제라면 쓰기를 중단하고 백업을 **새 DB명**으로 복원·검증한 뒤 .env.deploy의 DB_NAME을 복원 DB로 변경해 재기동한다. 원본 DB는 원인 조사용으로 남긴다. DB만 과거로 돌아가면 백업 이후 쓰기가 유실될 수 있어 복원 시점·사용자 영향을 확인해야 한다. 소유권/권한과 API readiness까지 확인한 후 서비스를 재개한다.

## 10. 문제 해결

| 증상 | 확인·조치 |
|---|---|
| Docker daemon 연결 실패 | Docker Desktop 실행, `docker version` 확인 |
| 포트 already in use | 15434/16381/18081 사용 프로세스 확인. 다른 스택을 지우지 말고 종료 대상을 구분 |
| DB connection refused | DB healthy, sandbox 프로필 순서 local,sandbox, URL/포트 확인 |
| schema unavailable / relation does not exist | 대상 DB 백업 → migration → 재기동. health만으로 전체 권한 검증을 대신하지 않음 |
| Hibernate validate 실패 | 잘못된 DB 연결 또는 스키마 미적용. ddl-auto=update로 우회하지 않음 |
| too many clients | 중복 테스트·서버 종료 후 단독 재실행 |
| 401 | 같은 서버 재로그인, Bearer 접두사, 토큰 만료 확인 |
| 403 | ADMIN 권한 또는 CORS origin 확인 |
| 뉴스 본문 SKIP/404 | 실제 기사 ID/샘플 데이터 확인 |
| 400/405 | 파라미터·JSON enum·HTTP 메서드 확인. 뉴스 조회는 GET |
| health 정상인데 AI 실패 | AI /health, 모델 목록·주소·토큰·메모리·타임아웃 확인 |
| 변경했는데 그대로 | bootRun/JAR/컨테이너 중 실제 프로세스 확인, 이미지 재빌드·재생성 |
| API 500 | 발생 시각·URL·상태·X-Request-ID·서버 예외 확인. 비밀번호/토큰은 기록하지 않음 |

최초 제보 500은 오류가 난 실제 서버가 아직 특정되지 않았다. 이 문서의 로컬 성공을 그 서버의 해결 완료로 간주하지 않는다. 상세 진단은 [API_500_DIAGNOSIS.md](API_500_DIAGNOSIS.md), 전체 미완료 항목은 [FOLLOWUP_WORK_2026-10-13.md](FOLLOWUP_WORK_2026-10-13.md)를 참고한다.
