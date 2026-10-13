# 실제 AI·크롤링·프론트 테스트 환경 검증

## 실행 기준

시작 코드: develop 1714e2e. 로컬 Ollama qwen3:8b(Q4_K_M), nomic-embed-text(768차원).
사용자가 수정 중인 기존 API 명세/진도 문서는 별도로 유지한다.

실제 AI 재현 명령:

```sh
cd ai-service
PORT=3002 HOST=127.0.0.1 npm start
# 다른 터미널: 백엔드 루트
RUN_AI_SMOKE=true ./gradlew test --tests com.youthletter.LiveAiIntegrationTest --no-daemon
```

테스트 DB `localhost:5433/youthletter_test`와 Redis 6380이 필요하다.
이 테스트는 실제 모델을 사용하며 외부 뉴스 페이지에 접속하므로 일반 CI에서는 제외한다.
원문 데이터와 생성 결과는 `build/reports/live-ai/fixture.json`, `crawled.json`에 저장한다.
실제 기사 전체를 Git에 복제하지 않는다. DB 테스트 쓰기는 트랜잭션 종료 후 rollback한다.

검사 항목:

- 실제 한경 목록에서 URL 추출 → 기사 1건의 제목·본문 파싱.
- 요약·용어 위치와 768차원 임베딩 생성 및 PostgreSQL 저장.
- 카드 5~7개 생성, 순서·길이·근거 문장 검사, 저장 후 재조회.
- 퀴즈 3문제 생성, 보기·정답·해설·근거 검사 및 자기 풀이 교차 확인.
- 중복 퀴즈 저장 방지, 공개 본문의 정답 비노출.
- 오답 0꿀 → 정답 10꿀 → 재제출 0꿀. 3문제 총 30꿀 확인.

주의: 같은 모델의 자기 풀이 일치는 사실 정확성의 보증이 아니다. 출력 원문 대조와
더 많은 기사에 대한 평가가 추가로 필요하다. 모델 응답 성공과 내용 품질은 따로 판단한다.

## 앱에서 바로 쓸 수 있는 slides 응답

```http
GET /api/news?page=1&limit=10&category=finance
Authorization: Bearer <로그인한 경우 accessToken>
```

`limit`를 명시하면 새 앱 형식, 기존 `size`를 쓰거나 `limit`가 없으면 기존 Spring Page 형식이다.
새 형식은 page 기본값 1, limit 1~100. category·keyword는 선택이다.

```json
{
  "status": 200,
  "data": [{
    "id": "12",
    "title": "기사 제목",
    "description": "저장된 요약",
    "category": "finance",
    "createdAt": "2026-10-11T12:00:00+09:00",
    "isSaved": false,
    "slides": [
      {"id":"101","type":"image","title":"첫 장","description":"첫 장 본문"},
      {"id":"102","type":"content","title":"둘째 장","description":"둘째 장 본문"}
    ]
  }],
  "meta": {"page":1,"limit":10,"hasNext":false}
}
```

위 JSON은 형태 설명용이다. 실제 카드는 저장된 내용을 순서대로 반환하며, 게시된 카드만 노출한다.
카드 생성 전에는 `slides: []`이다. 조회 요청에서 AI를 새로 실행하지 않는다.
이미지가 없으면 image를 생략한다. 첫 슬라이드는 image 타입이며 앱 기본 배경을 사용할 수 있다.
비로그인 isSaved는 false, 로그인 시 본인 저장 여부다. ID는 서버가 준 문자열 그대로 사용한다.
createdAt은 기존 publishedAt 값을 JVM/JDBC 기본 시간대 기준으로 offset을 붙여 전송한다.
서버 시간대는 고정해서 운영해야 하며 검증 Compose는 Asia/Seoul을 사용한다.
슬라이드는 페이지의 기사 ID들로 일괄 조회한다. 기존 상세 `/api/card-news/article/{id}`도 유지한다.

## 별도 프론트 검증 환경

```sh
docker compose -p dongle-ai-verify -f docker-compose.verify.yml up -d --build
curl http://localhost:18080/api/health
curl 'http://localhost:18080/api/news?page=1&limit=10'
```

- API: 18080, PostgreSQL: localhost:15433, Redis: localhost:16380.
- 기존 개발 DB와 다른 볼륨 사용. 처음 생성 시 최신 스키마와 샘플 데이터를 초기화한다.
- 샘플은 직접 작성한 교육용 데이터이며 실제 AI 생성물과 구분한다.
- 자동 크롤링·AI 작업자·푸시 발송은 꺼져 있다. 조회/로그인 등은 AI 없이 시험 가능하다.
- 앱 `EXPO_PUBLIC_API_URL=http://맥북IP:18080` 설정 후 Expo 재시작.
- 같은 네트워크 밖의 팀원은 이 사설 IP에 접속할 수 없다. 이 환경은 외부 배포가 아니다.
- 테스트 계정은 회원가입으로 만든다. init.sql의 더미 비밀번호 계정을 로그인 계정으로 안내하지 않는다.
- 종료: `docker compose -p dongle-ai-verify -f docker-compose.verify.yml stop` (데이터 보존).
- 기존 볼륨은 초기화 SQL을 재실행하지 않는다. 앞으로 스키마 변경 시 별도 마이그레이션 필요.

실제 AI API를 이 컨테이너에서 호출하려면 host.docker.internal:3002 접속을 별도 확인해야 한다.
Node AI는 로컬 바인딩을 기본으로 사용하며, 외부 바인딩 시 서비스 토큰 설정이 필수다.
