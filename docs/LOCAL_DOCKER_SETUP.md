# 로컬 Docker로 백엔드 + DB 실행하기 (프론트 팀원용)

검증: 2026-10-11, GitHub `develop`을 새로 clone한 상태에서 `docker-compose.local.yml`로 처음부터 실행해 확인했다.

## 한 번에 실행

```bash
git clone https://github.com/Dongle-2026/Dongle-BE.git
cd Dongle-BE
docker compose -f docker-compose.local.yml up -d --build
```

처음에는 이미지 빌드 때문에 몇 분 걸린다. 끝나면 컨테이너 3개가 뜬다.

| 컨테이너 | 내용 | 주소 |
|---|---|---|
| youthletter-postgres | PostgreSQL 16 + pgvector | localhost:5432 |
| youthletter-redis | Redis 7 | localhost:6379 |
| youthletter-api | Spring 백엔드 | http://localhost:8080 |

## 정상인지 확인하는 4가지

DB가 켜지는 것과 데이터가 들어 있는 것은 별개라서 각각 확인한다.

```bash
docker ps --filter name=youthletter --format '{{.Names}} {{.Status}}'
```
① 세 컨테이너가 모두 `healthy`.

```bash
curl http://localhost:8080/api/health
```
② `"database":"connected","redis":"connected"` → 백엔드가 DB에 연결됨.

```bash
docker logs youthletter-postgres 2>&1 | grep "apply V"
```
③ `apply V16 …` ~ `apply V26 …` 11줄 → 테이블 생성 완료.

```bash
curl "http://localhost:8080/api/news?size=3"
```
④ `totalElements` 8 → 샘플 뉴스가 들어 있음.

## 새 DB에 자동으로 들어가는 것

| 순서 | 파일 | 내용 |
|---|---|---|
| 1 | `docker/postgres/init.sql` | 기본 테이블 + 용어 10개, 정책 3개 |
| 2 | `docker/postgres/02-apply-migrations.sh` | 추가 테이블(upgrade + V16~V26): 퀴즈, 꿀, 알림 설정, 프로필 사진, 검색 기록, 푸시 기록 등 |
| 3 | `docker/postgres/03-sample-data.sql` | 샘플 기사 8건(카테고리별), 용어 하이라이트, 퀴즈 6문제 |

- 샘플 기사는 화면 시험용으로 직접 쓴 문장이다(제목에 `[샘플]`). 실제 뉴스가 아니다.
- 샘플 기사 2건(청년·경제)에 퀴즈가 있어 AI 없이도 본문·퀴즈·정답 보기·꿀을 시험할 수 있다.
- 로컬은 AI stub 모드라 새 퀴즈는 만들어지지 않는다(퀴즈 없는 기사는 `quizStatus: UNAVAILABLE`).

## 계정

`init.sql`의 test@/admin@ 계정은 비밀번호가 없는 더미라 **로그인할 수 없다.**
앱(또는 `POST /api/auth/signup`)에서 시험용 계정을 새로 만들어 쓴다. 실제 쓰는 비밀번호를 넣지 않는다.

## 이미 예전에 DB를 만든 경우

Docker는 DB 볼륨이 **처음 만들어질 때만** 위 스크립트를 실행한다. 예전에 한 번 띄운 적이 있으면 테이블이 빠져 있을 수 있다
(증상: 퀴즈·꿀·트렌드 API 오류, 로그에 `relation "ai_jobs" does not exist`).

데이터를 지우지 않고 추가 테이블만 적용:

```bash
docker exec youthletter-postgres sh /docker-entrypoint-initdb.d/02-apply-migrations.sh
```

여러 번 실행해도 안전하다. 샘플 데이터까지 새로 받고 싶고 **로컬 DB 데이터를 지워도 될 때만**:

```bash
docker compose -f docker-compose.local.yml down -v
docker compose -f docker-compose.local.yml up -d --build
```

`-v`는 로컬 DB와 Redis 데이터를 지운다. 다은이 맥의 원본 DB처럼 보존해야 하는 데이터가 있는 컴퓨터에서는 쓰지 않는다.

## 앱에서 연결

- 시뮬레이터/같은 맥: `http://localhost:8080`
- 실제 휴대폰: `http://<맥 IP>:8080` (휴대폰의 localhost는 휴대폰 자신)
- API 명세: `docs/API_SPEC_2026-10-11.md`, Swagger `http://localhost:8080/swagger-ui.html`

## 종료

```bash
docker compose -f docker-compose.local.yml down
```

`-v` 없이 내리면 데이터는 남는다.
