# 빠른 수집 파이프라인

`./run-pipeline`은 전용 DB에 원문을 먼저 보관하고, 기사별 결과를 묶어서 저장합니다.

- 단순 요약은 원문 앞부분의 최대 3문장을 발췌합니다.
- 모델 호출 최대 1회: 원문에 존재하는 용어 최소 5개 및 쉬운 설명. 기존 사전에 해당 용어가 5개 이상 있으면 설명을 재사용합니다.
- 카드: 원문 문장을 중요 용어 기준으로 선택한 2~5장. 본문을 재작성하지 않습니다.
- 퀴즈: 4지선다 3문제를 섞어 냅니다(`QUIZ_GENERATION_MODE=mixed`).
  1) 원문 빈칸: 단어 단위로만 빈칸을 만들고(합성어 중간 금지), 문제마다 다른 문장을 씁니다.
  2) 뜻 고르기: "기사에 나온 ‘양도차익’의 뜻으로 알맞은 것은?" — 보기는 같은 기사 용어의 사전 설명.
  3) 용어 고르기: "다음 설명에 해당하는 기사 속 용어는?" — 보기는 같은 기사의 용어.
  숫자가 든 설명·용어 이름이 드러나는 설명은 쓰지 않고, 못 만들면 빈칸 문제로 채웁니다.
- 수집: 한경 경제·증권(koreamarket)·부동산 섹션(`CRAWLER_SECTIONS`로 변경) + 매일경제 RSS 4개 피드(피드당 최대 10건, `CRAWLER_MAEKYUNG_ENABLED`). 주기는 언론사별로 매일경제 30분(`CRAWLER_MAEKYUNG_INTERVAL_MS`), 한경 1시간(`CRAWLER_HANKYUNG_INTERVAL_MS`)이며, 스케줄러는 30분마다 깨어 주기가 지난 수집기만 실행한다. 매일경제는 RSS로 기사를 찾고 원문 페이지에서 본문을 읽으며, 사이트가 차단(403·확인 페이지)하면 그 회차의 매일경제 수집만 건너뛴다. 카테고리는 두 언론사 모두 같은 분류기로 정한다. 사진 캡션은 본문에서 빼고, 본문 300자 미만(유료 기사 미리보기)은 건너뜁니다.
- 임베딩: 모델 입력 한도를 넘는 원문은 임베딩 API에서 자릅니다. DB 원문은 보존합니다.
- 용어가 5개 미만이면 완료로 처리하지 않습니다. 요약·용어·카드·퀴즈는 한 트랜잭션으로 저장됩니다.

이전 버전에서 용어 2개만 생성한 기사도 재실행 시 보완합니다. 이미 풀이 기록이 있을 수 있는 기존 퀴즈는 덮어쓰지 않습니다.
상태 출력의 `전체 완성`은 요약·카드·퀴즈 3개·용어 5개를 모두 갖춘 기사 수입니다.

기본 모델은 `gemma3:4b`입니다(기사당 약 40초, qwen3:8b는 약 110초). `.env.pipeline.local`의 `OLLAMA_CHAT_MODEL`로 바꿀 수 있습니다. 변경 후 `./run-pipeline`을 다시 실행합니다.
실행 중 현황만 조회하려면 `./run-pipeline --status`를 사용합니다. 새 결과 생성에 실패해도 기존 결과와 원문은 보존됩니다.

## 결과 확인

`http://localhost:18082/` — 백엔드 기능 확인 페이지(뉴스·요약·용어 하이라이트·카드뉴스·퀴즈·용어 사전·계정·API 콘솔).

## 다른 DB로 옮기기

완성된 콘텐츠만 다른 DB(개발·운영)에 넣는다. 대상에 이미 있는 기사(같은 원문 URL)와 사용자 기록은 건드리지 않는다.

```sh
python3 scripts/import_content.py --target-container 대상_postgres_컨테이너 --target-db DB이름 --target-user DB사용자 --dry-run
python3 scripts/import_content.py --target-container 대상_postgres_컨테이너 --target-db DB이름 --target-user DB사용자
```

대상 DB는 이 저장소 마이그레이션(V28까지)이 적용돼 있어야 한다. 배포 절차는 `docs/LOCAL_TEST_AND_DEPLOY.md` 9절.

## 앱(Expo)에서 이 서버로 카카오 로그인 시험

`.env.pipeline.local`에 아래를 넣고 `./run-pipeline`(또는 backend만 재기동)한다. 키 값은 Git에 올리지 않는다.

```
PIPELINE_API_BIND=0.0.0.0                      # 같은 와이파이의 폰에서 접속(DB는 계속 이 맥에서만)
OAUTH2_REDIRECT_URI=exp://<Expo 주소>/--/oauth/kakao   # 로그인 후 앱으로 돌아갈 주소
KAKAO_CLIENT_ID=...
KAKAO_CLIENT_SECRET=...
```

카카오 개발자 콘솔의 Redirect URI에는 백엔드 주소 `http://<맥IP>:18082/login/oauth2/code/kakao`를 등록한다(와이파이가 바뀌면 맥 IP도 바뀐다). 앱은 `http://<맥IP>:18082/oauth2/authorization/kakao`를 열면 되고, 로그인 후 `exp://…?accessToken=…&refreshToken=…`로 돌아온다.
