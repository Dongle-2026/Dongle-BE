# 한 명령으로 기사·카드뉴스·퀴즈 저장

프로젝트 폴더에서 `./run-pipeline`을 실행한다. Mac에 Docker Desktop, Ollama, Python 3가 설치되어 있어야 한다. 실행기는 Docker/Ollama를 켜고, 없는 모델(qwen3:8b, nomic-embed-text)을 내려받고, 서버를 빌드해 실행한다. 최초에는 다운로드·빌드 시간이 필요하다.

```sh
./run-pipeline
```

시작 약 10초 후 한경 경제 목록에서 신규 기사를 수집한다. 원문과 ARTICLE/CARD/QUIZ 작업을 한 트랜잭션으로 저장하고 AI 작업자가 순차 생성·검증·DB 저장한다. 이후 수집 완료 기준 6시간마다 반복한다. 기사별 결과를 별도로 저장하므로 한 작업이 실패해도 다른 작업은 처리된다. 카드뉴스는 앱이 표시하는 카드 데이터이며 이미지 파일/PDF가 아니다.

터미널을 열어 두고 전원·인터넷을 유지한다. 실행 중 `caffeinate`로 유휴 잠자기를 방지하지만 MacBook 덮개는 열어 둔다. Ctrl+C는 백엔드/AI 컨테이너를 중지하고 잠자기 방지를 해제한다. DB/Redis는 남아 있으며 `./run-pipeline --stop`으로 함께 중지할 수 있다. Ollama는 다른 작업에서도 사용할 수 있어 종료하지 않는다.

## 한 번만 생성 / 진행 확인

```sh
./run-pipeline --once --limit 1   # 새 기사 1건으로 전체 흐름 확인
./run-pipeline --once            # 현재 목록의 신규 기사 모두 처리하고 종료
./run-pipeline --status          # DB에 저장된 결과·실패 원인 확인
./run-pipeline --retry-failed    # 실패한 AI 작업만 재접수하고 정기 실행
```

`--limit`은 회차당 신규 수집 제한이며 이미 접수된 작업은 모두 이어서 처리한다. `--once`의 기본 제한 시간은 6시간(`--timeout` 초 단위로 변경). 실패가 있으면 성공 메시지를 출력하지 않고 종료 코드 1을 반환한다. 새 기사가 없을 때는 0건으로 완료될 수 있다. 종료/재부팅 중 RUNNING이었던 작업은 기존 10분 리스 만료 후 재시도하며, 최대 시도 횟수 3회를 넘으면 실패로 남는다. 네트워크/AI 오류는 최대 3회 자동 시도한다. 검증 실패도 실패로 남기며 가짜 결과로 대체하지 않는다.

## 저장 위치와 설정

- 전용 Docker 프로젝트: `youthletter-pipeline`
- 영속 DB 볼륨: `youthletter-pipeline_pipeline_db` (컨테이너 중지/재시작으로 지워지지 않음)
- DB: `localhost:15436`, database `youthletter_pipeline`, user `youthletter`
- API: `http://localhost:18082` (실행 중), 프론트 API 주소를 이곳으로 맞춰야 이 DB의 결과가 보임
- 원문 `news_articles`, 요약 `news_summaries`, 카드 `card_news`/`card_news_cards`, 퀴즈 `quizzes`, 작업 `ai_jobs`, 수집 현황 `content_pipeline_runs`
- 비밀번호/토큰: 최초 자동 생성되는 `.env.pipeline.local` (권한 600, Git 제외). **이 파일을 삭제하면 기존 DB 암호와 달라지므로 보관한다.**
- 기존 개발/검증 DB를 합치거나 덮어쓰지 않는다. 운영 DB 이전은 별도 작업이다.

`.env.pipeline.local`에 `PIPELINE_API_PORT`, `PIPELINE_DB_PORT`, `PIPELINE_INTERVAL_MS`, `OLLAMA_CHAT_MODEL`을 추가해 설정할 수 있다. 임베딩은 기존 768차원 스키마와 맞는 모델을 유지한다. 소스 변경 후 기본 명령은 재빌드하며, 변경이 없으면 Docker 캐시를 사용한다. `--no-build`는 이미 만든 이미지에만 사용한다.

컨테이너 로그:

```sh
docker compose -p youthletter-pipeline --env-file .env.pipeline.local -f docker-compose.pipeline.yml logs --tail=100 backend ai-service
```

현재 수집 범위는 한경 경제 목록에 노출된 기사다. 과거 전체 기사 수집은 포함하지 않는다. 원문 요청에 1초 간격을 두고 429 응답 시 수집을 중단한다. AI 호출 시간과 품질에 따라 완료 시간이 달라지며 카드/퀴즈는 기존 검증기를 통과한 결과만 저장한다.
