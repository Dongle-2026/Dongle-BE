# 최신 앱 기준 API 연결 명세와 구현 순서

## 1. 확인한 코드와 결론

2026-10-11 `Dongle-2026/Dongle-APP`를 fetch하여 원격 main **9fa18d9**를 읽었다.
로컬 앱은 **b1c9911**, `codex/iphone-local-setup` 브랜치이며 실제 API 연동 작업이 있다.
두 버전은 서로 다른 변경을 포함한다. fetch만 했으며 checkout/merge/push하지 않았다.
최신 화면에 맞게 기존 인증·통신 코드를 통합해야 한다. 최신 main을 실행하는 것만으로
이전에 개발한 백엔드 연결 기능이 포함되지는 않는다.

| 화면/기능 | 최신 main의 근거 파일 | 현재 상태 | 연결 작업 |
|---|---|---|---|
| 뉴스 목록·상세·검색·저장 | services/newsService.ts | setTimeout 기반 가짜 응답, 실제 요청은 주석 | 기존 로컬 서비스의 서버 DTO 변환 재사용 |
| 검색 홈 | app/(tabs)/search.tsx | RECENT_SEARCHES, TRENDING_KEYWORDS, KEYWORD_NEWS 사용 | 최근 검색은 기기 저장, 인기검색은 실제 집계 후 제공 |
| 마이페이지 | app/(tabs)/mypage.tsx | mockProfile/Stats/Activity/Settings 사용 | 인증된 본인 데이터 로딩·오류·빈 상태 적용 |
| 프로필 수정 | services/profileService.ts | 로컬 이미지 URI와 문자열을 반환, 서버 업로드 없음 | 파일 저장 및 multipart API 필요 |
| 용어 저장 | components/news/news-body.tsx | 화면의 Set에 단어명만 저장 | 안정적인 termId와 독립 저장 테이블 필요 |
| 퀴즈 | components/quiz/QuizMode.tsx | answerIndex와 honey로 클라이언트 채점 | 정답 비공개, 서버 채점·보상 원자적 기록 필요 |
| 공통 통신 | utils/api.ts | 기본 localhost:3000, 토큰 처리 없음, 응답 wrapper를 벗기지 않음 | 기존 로컬 인증·refresh·8080 연결 코드 통합 |

커밋 메시지에 '이미지 업로드 연동'이 있어도 실제 코드는 mock이다. 구현 여부는
커밋 제목이 아니라 서비스 코드와 서버 저장 결과로 판정한다.

## 2. 우리가 정하는 연결 원칙

사용자는 요청·응답 JSON을 별도로 설계할 필요가 없다. 백엔드가 데이터 보관·인증·검증을
담당하고 앱 서비스 계층이 서버 DTO를 화면 타입으로 변환한다.

- 최신 UI를 보존하고, 로컬의 검증된 통신·인증 코드를 통합한다.
- 기존 앱과 서버가 사용하는 wrapper와 페이지 구조는 당장 전역 변경하지 않는다.
- 화면에는 mock을 성공 데이터로 표시하지 않는다. 미구현 기능은 준비 중으로 구분한다.
- 화면 타입에 없는 데이터는 만들어 채우지 않는다. LOCAL 계정을 google로 표시하지 않는다.
- 퀴즈 점수·배지·인사이트를 근거 없이 0 또는 랜덤 값으로 완료 처리하지 않는다.
- 뉴스와 학습 용어 저장은 별도 기능이다. '학습 완료'로 '저장'을 대체하지 않는다.
- 이 문서는 서버 전송 DTO와 앱 화면 모델을 구분한다. 노션의 모든 공통 규칙에 맞춘 완료 명세는 아니다.

## 3. 현재 사용할 수 있는 기본 계약

기본 주소는 Spring 서버 `http://<맥북 또는 배포 서버 주소>:8080`이다.
아이폰에서 localhost는 맥북을 의미하지 않는다. 비밀키는 EXPO_PUBLIC 변수에 넣지 않는다.

성공 JSON 예시:

```json
{"status":200,"message":"success","data":{"bookmarked":true}}
```

message 문구는 API에 따라 달라질 수 있으며 로직 분기에 사용하지 않는다.
오류는 HTTP 상태를 우선 사용한다. 현재 body.status는 4001 같은 내부 코드일 수 있다.
앱의 ApiErrorResponse로 변환할 때 HTTP status와 서버 내부 코드를 혼동하지 않는다.
보호된 요청에는 `Authorization: Bearer <accessToken>`을 넣는다.

| 기능 | 요청 | 응답 data / 주의점 |
|---|---|---|
| 토큰 갱신 | POST /api/auth/refresh, JSON {refreshToken} | accessToken, refreshToken, nickname. 새 refresh도 함께 보관 |
| 뉴스 목록 | GET /api/news?page=0&size=10&keyword=금리 | Page의 content, number, size, last, totalElements 사용 |
| 검색 | GET /api/news/search?q=금리&page=0&size=10 | 같은 Page 구조 |
| 상세 | GET /api/news/{id} | 원문·요약·termHighlights. 화면 본문 데이터로 변환 |
| 저장 | POST /api/news/{id}/save | {bookmarked:true}, 반복 호출 안전 |
| 해제 | POST /api/news/{id}/unsave | {bookmarked:false}, 반복 호출 안전 |
| 읽음 | POST /api/news/{id}/read | 한국 시간 같은 기사 하루 1회. 성공 data가 없을 수 있음 |
| 내 정보 | GET /api/me | 아래 프로필 예시 |
| 학습 통계 | GET /api/me/stats | readNewsCount, learnedTermCount, consecutiveLearningDays |
| 저장 뉴스 | GET /api/me/saved-news?page=0&size=10 | Page<뉴스 북마크>, articleId를 화면 id로 변환 |
| 용어 저장 | POST /api/terms/{termId}/save | {bookmarked:true}, 반복 호출 안전. 학습 완료(learn)와 별개 |
| 용어 해제 | POST /api/terms/{termId}/unsave | {bookmarked:false}, 학습 이력은 지우지 않음 |
| 저장 용어 | GET /api/me/saved-terms?page=0&size=10 | Page: termId, name, definition, simpleDefinition, path, difficultyLevel, savedAt. 최근 저장 순 |
| 최근 읽음 | GET /api/me/read-history?page=0&size=10 | Page: articleId, title, sourceName, category, publishedAt, lastReadAt. 기사별 1건, 최근 읽은 순 |
| 기기 등록 | POST /api/me/devices, JSON {deviceId, token, platform: IOS/ANDROID} | 같은 deviceId면 토큰 교체. 등록 성공≠실제 푸시 수신 |
| 회원 탈퇴 | DELETE /api/me | soft delete + 토큰 무효화 + 기기 삭제. 이후 같은 토큰은 401 |

기존 `/api/users/me`, `/api/users/me/learning-stats`, `/api/users/me/bookmarks`,
`/api/auth/reissue`, PUT/DELETE `/api/news/{id}/bookmark`도 유지한다.

프로필 서버 data 예시(설명용, 실제 사용자 데이터 아님):

```json
{
  "id": 123,
  "email": "example@example.com",
  "name": "사용자",
  "nickname": "돈글",
  "birthDate": null,
  "gender": null,
  "regionCode": null,
  "occupation": null,
  "incomeLevel": null,
  "interests": []
}
```

필드의 null은 직렬화 설정에 따라 생략될 수 있다. 현재 서버에는 bio, avatarId,
oauthProvider, joinedAt, profileImageUrl이 이 DTO에 없다. 최신 앱의 UserProfile 타입과
동일하지 않으므로 그대로 캐스팅하면 안 된다. 새 프로필 스키마/DTO 작업 전까지
닉네임 등 실제 제공되는 항목만 표시해야 한다.

통계 서버 data 예시:

```json
{"readNewsCount":4,"learnedTermCount":2,"consecutiveLearningDays":3}
```

앱 streak는 consecutiveLearningDays에서 변환한다. totalQuizCount, totalHoney는
퀴즈 서버 데이터가 만들어지기 전까지 '준비 중'으로 표시한다.

앱 변환 규칙:

```text
앱 page=1, limit=10 → 서버 page=0, size=10
서버 data.content → 화면 목록
서버 data.last=false → 다음 페이지 있음
서버 숫자 id → String(id), 서버 요청 시 실제 ID 사용(news- 접두사 생성 금지)
서버 sourceName → 화면 source
서버 bookmarkedAt → 화면 savedAt
```

서버 날짜(일시)는 **한국 시간 + 오프셋** ISO 8601로 응답한다(2026-10-18 변경).
예: `"publishedAt": "2026-10-18T09:00:03+09:00"`. 서버 배포 시간대(UTC/Asia/Seoul)와 관계없이
같은 시각이 나온다. 앱은 `new Date(value)`/`DateTime.parse(value)`로 그대로 읽으면 된다.
날짜만 있는 값(LocalDate, 예: spentAt)은 기존처럼 `2026-10-18` 형식이다.

## 4. 신규 설계할 계약 — 아직 구현 완료 아님

### 용어 저장 — 서버 구현 완료(2026-10-11), 위 기본 계약 표 참고

- POST /api/terms/{termId}/save, /unsave: 토큰 필수, 반복 요청 안전.
- GET /api/me/saved-terms: id, term, definition, category, savedAt 제공 목표.
- termId는 DB 식별자로 전달. 현재 TermDefinition에는 ID가 없어 앱 타입 확장 필요.
- user_id + term_id 유니크로 중복 저장 방지. lookup log와 분리.

### 뉴스 본문과 퀴즈

- GET /api/news/{id}/body: intro, source, publishedAt, sections, terms, quizzes, tags로 변환하는 별도 계약 설계.
- 원문에 없는 소제목/예문/태그를 임의로 생성하지 않는다.
- 공개 퀴즈에는 id, question, choices만 전달하고 answerIndex를 제거한다.
- POST /api/quizzes/{quizId}/answer: selectedChoiceId만 입력. 정답·보상·해설은 서버 결과로 전달.
- 재요청은 기존 결과 반환, 포인트 중복 지급 금지. 첫 답변 확정/재응시/정답 보상은 사용자와 확인한다.
- 최신 앱은 정답과 honey를 직접 읽기 때문에 퀴즈 컴포넌트도 함께 수정해야 한다.

### 프로필 이미지

- PATCH /api/me/profile: nickname, bio, avatarId, image 또는 removeImage.
- undefined=변경 없음, null=삭제라는 앱 입력을 multipart 필드로 변환.
- FormData를 JSON.stringify하지 않고 boundary는 fetch가 설정하도록 한다.
- 서버 파일 크기/실제 MIME/이미지 디코딩 검증, 사용자 소유권, 저장 실패 복구 필요.
- 파일 저장 위치는 배포 환경과 함께 선택. 개발용 로컬 파일을 영구 저장으로 소개하지 않는다.

## 5. 개발 체크리스트

- [x] 최신 origin/main fetch 및 실제 코드 확인
- [x] 기존 로컬 API 연결 브랜치와 최신 UI 브랜치 차이 확인
- [x] 내 정보·기본 학습 통계·저장 뉴스의 노션 경로 추가
- [ ] 최신 UI와 기존 인증/통신 코드 통합 (아직 merge하지 않음)
- [ ] 최신 화면에서 목록·검색·상세·저장·읽음 연결
- [x] 독립 용어 저장, 최근 읽음, 기기 등록, 탈퇴 서버 API (V19 term_bookmarks)
- [ ] 위 API의 화면 연결, 활동(히트맵) API
- [ ] 프로필 확장, 이미지 저장, 알림 설정
- [ ] 퀴즈 정책 확정 → 서버 채점 → 최신 퀴즈 화면 연결
- [ ] OAuth 실제 앱 콜백·제공자 설정
- [ ] 앱 타입 검사·통신 테스트·iOS 번들·실제 iPhone 확인

기존 main은 수정하지 않았고 원본 DB도 변경하지 않았다. 다음 통합 작업에서 원격 최신
UI와 로컬 기능을 모두 보존하는 충돌 해결이 필요하다. 단순 pull로 끝나는 상황이 아니다.

## 6. 이번 변경 검증

`RUN_NOTION_API_AUDIT=true ./gradlew test --no-daemon` 실행 결과: 112건 중 111건 성공,
실제 AI 추론 전용 1건 제외, 실패 0건. /api/me 3개 경로는 익명 401, 로그인 시 200,
본인 ID·학습 수·저장/해제 후 목록 개수를 검증했다. 원본 DB가 아닌 테스트 DB를 사용했다.
노션 목록 29개 중 메서드·경로 등록은 현재 11개다. 감사 테스트에는 미구현 경로가
없음을 확인하는 테스트도 있으므로 전체 통과가 29개 구현 완료를 뜻하지 않는다.

### 추가 검증 (A 그룹: A12·A13·A16·A18·A28·A29)

`RUN_NOTION_API_AUDIT=true ./gradlew test --rerun --no-daemon`: 117건 중 116건 성공, 실제 AI 전용 1건 제외, 실패 0건.
노션 경로 등록 17/29. 테스트 DB(5433) 대상으로 실제 서버(포트 18081)를 띄워 HTTP 스모크도 확인했다:
용어 저장 동시 20회 → 200 20건·DB 1행, 익명 401, 없는 용어 404, 읽음 2회 → 이력 1건, 기기 등록 2회 → 1행(최신 토큰),
DELETE /api/me 후 같은 토큰 401·기기 0건. 스모크 데이터는 삭제했다. 실제 iPhone 화면 연결은 아직 미확인.

## 7. 2026-10-11 추가 구현 — 노션 29개 경로 전부 등록

공통 규칙(3장)은 그대로다. 성공 `{status, message, data}`, 목록은 Spring Page(`content`, `last`), page는 0부터,
ID는 숫자, 일시는 한국 시간 오프셋 포함 ISO 8601(예: `2026-10-18T09:00:03+09:00`). **null 필드는 JSON에서 생략**되므로 앱은 "없음 = null"로 처리한다.

### 인증 (A1)

| 요청 | 응답 data | 비고 |
|---|---|---|
| `POST /api/auth/oauth/{kakao\|google}` `{idToken, nonce?}` | `accessToken, refreshToken, nickname, newUser` | 앱 SDK가 받은 OIDC idToken. nonce를 SDK 로그인에 넣었다면 같은 값을 보낸다 |

- 401 `4006` 토큰 검증 실패, 409 `4007` 같은 이메일이 다른 방식으로 이미 가입됨(자동 연결 안 함, "기존 방식으로 로그인" 안내), 503 `5004` 서버에 제공자 설정 없음.
- `newUser=true`면 온보딩(관심사 등) 화면으로 보낸다.

### 프로필 (A8 확장, A25)

`GET /api/me` 응답에 `bio, avatarId, profileImageUrl, oauthProvider(kakao|google, 이메일 가입은 없음), joinedAt` 추가.

`PATCH /api/me/profile` — **multipart/form-data**. 필드: `nickname`, `bio`(빈 문자열=삭제), `avatarId`, `image`(파일), `removeImage=true`.
보내지 않은 필드는 유지. `image`와 `removeImage`를 같이 보내면 400. 응답은 수정 후 프로필 전체.

- 사진은 서버에서 긴 변 512px JPEG로 줄여 DB에 저장(실측: 4000px 1.3MB → 31KB). EXIF(위치정보)는 제거.
- 허용: JPEG, PNG(그대로 재인코딩), HEIC/HEIF/WebP(변환 없이 저장, 1MB 이하). 업로드 최대 5MB.
- **권장**: 이미 `allowsEditing: true, quality: 0.8`로 고르면 JPEG가 와서 문제없다. HEIC 원본을 보내지 말 것.
- 413 `4106` 용량 초과, 415 `4107` 지원하지 않는 형식, 400 `4108` 손상된 파일.
- `profileImageUrl`은 인증 없이 열리는 URL(추측 불가 키). 사진을 바꾸면 URL이 바뀌므로 캐시 걱정 없음.

### 알림 설정 (A26, A27)

`GET/PATCH /api/me/notification-settings` — `{dailyDigest, quizReminder, streakAlert}`. 저장 전 기본값은 모두 `true`.
PATCH는 보낸 항목만 바뀌고 전체 설정을 돌려준다. (실제 푸시 발송은 아직 없음. 설정 저장만 된다.)

### 카테고리·검색 (A20, A21, A23, A24)

| 요청 | 응답 data |
|---|---|
| `GET /api/categories` | `[{id, name, isNew}]` 8개: youth 청년, policy 정책, finance 금융, investment 투자, real-estate 부동산, economy 경제, job 취업, saving 저축. **'전체'는 앱에서 앞에 붙인다** |
| `GET /api/news?category=youth` | id·표시명 둘 다 허용 |
| `GET /api/keywords?limit=8` | `[{id, keyword, category, categoryName, articleCount}]`. `image`는 기사 썸네일이 없어 **항상 없음** → 앱 기본 이미지 사용. 카드 탭 시 `GET /api/news/search?q={keyword}` |
| `GET /api/search/trending?limit=10` | `[{rank, keyword, change(up\|down\|same), isNew}]` |
| `GET /api/search/suggestions?q=금리` | `[{keyword, type(term\|trending)}]` 최대 8개 |

- isNew: 최근 24시간 안에 그 카테고리 기사가 들어옴.
- 키워드 카드: 최근 7일 기사에 하이라이트된 용어를 기사 수 순으로.
- 트렌드: 최근 24시간 **검색한 사용자 수** 순위(한 사람이 여러 번 검색해도 1), 직전 24시간과 비교. 로그인 사용자의 첫 페이지 검색만 집계, 30일 후 삭제.
- 최근 검색어(RECENT_SEARCHES)는 서버 API가 없다. 기기에 저장한다.

### 본문·퀴즈·꿀 (A10, A11, A9 확장, A19, A22)

`GET /api/news/{id}/body` (로그인 선택):

```json
{"newsId":1,"title":"...","intro":"AI 요약(없으면 생략)","source":"한경","publishedAt":"...",
 "sections":[{"id":"s1","text":"원문 문단"}],
 "terms":[{"termId":7,"term":"ETF","definition":"쉬운 설명","category":"금융","saved":true}],
 "quizStatus":"READY","quizzes":[{"id":31,"question":"...","choices":[{"id":"a","text":"..."}],"honey":10,
   "myResult":{"attempts":1,"solved":true,"earnedHoney":10}}],
 "tags":[]}
```

- **정답(answerIndex)·해설은 본문에 없다.** 앱의 클라이언트 채점을 서버 제출로 바꿔야 한다.
- 소제목은 원문에 없어서 만들지 않는다(`subtitle` 없음). `tags`는 데이터가 없어 빈 배열.
- `quizStatus`: `READY` / `GENERATING`(AI가 만드는 중, 1~2분 뒤 다시 조회) / `UNAVAILABLE`(지금은 퀴즈 없음, 퀴즈 영역 숨김).
- `terms[].termId`로 `POST /api/terms/{termId}/save` 호출. 앱 `TermDefinition`에 termId 추가 필요.

`POST /api/quizzes/{quizId}/answer` `{"choiceId":"b"}` 또는 `{"selectedIndex":1}` (로그인 필수):

```json
{"quizId":31,"selectedIndex":1,"isCorrect":true,"earnedHoney":10,"alreadySolved":false,"correctIndex":1,"explanation":"..."}
```

- **재도전 가능.** 오답이면 `correctIndex`·`explanation`이 없다(정답을 알려주면 재도전 의미가 없음). 화면에서 "다시 풀기"를 제공한다.
- 꿀: 문제당 **첫 정답 10꿀**, 다시 맞히면 0꿀 + `alreadySolved=true`. 기사 읽음 **하루 기사당 5꿀**(`POST /api/news/{id}/read` 시 자동).

`GET /api/me/stats` 추가 필드: `todayCompleted`(오늘 한국 날짜 기사 1개 이상 읽음), `totalHoney`, `totalQuizCount`(제출한 문제 수).
앱 `streak` = `consecutiveLearningDays`.

`GET /api/me/activity?days=126` → `[{date:"2026-10-11", level:0~4, honey}]` 오래된 날부터, 빈 날도 포함(최대 365일).
level: 0꿀=0, 1~10=1, 11~25=2, 26~40=3, 41+=4.

`GET /api/me/insights` → `{correctRate, totalQuizCount, interestedCategories[], weakCategories[]}`

- correctRate: 문제별 첫 시도 정답률(%). 푼 문제가 없으면 없음 → "아직 퀴즈 기록이 없어요".
- interestedCategories: 최근 30일 많이 읽은 카테고리 최대 3개.
- weakCategories: 3문제 이상 푼 카테고리 중 첫 시도 정답률 60% 미만, 최대 2개. 표본 부족이면 빈 배열.

`GET /api/me/read-history` 항목에 `honey`(그 기사에서 받은 꿀 합계) 추가. `thumbnail`은 데이터 없음.

### 검증 (develop 통합 후)

`RUN_NOTION_API_AUDIT=true ./gradlew test --rerun`: 150건 중 149건 성공, 실제 AI 전용 1건 조건부 제외, 실패 0건.
AI 서비스 `npm test` 16건 성공. 노션 29개 경로 모두 등록·정상 응답(A1은 실제 idToken이 필요해 경로 도달만 감사, 서명 검증은 전용 테스트).
테스트 DB를 쓰는 실제 서버(18081)에서 기능별 HTTP 확인: 동시 요청 시 꿀·저장·가입·설정이 1회만 반영됨,
실제 로컬 모델(qwen3:8b)로 퀴즈 생성 161초·저장·채점까지 확인. 실제 iPhone, 실제 카카오/구글 로그인은 미확인.

서버 환경 변수(운영자가 설정): `GOOGLE_MOBILE_CLIENT_IDS`, `KAKAO_APP_KEYS`(카카오 OIDC 활성화 필요).
DB 적용: `docs/INTEGRATION_STATUS.md`의 V19~V23 순서, 이후 `POST /api/admin/news/reclassify`.
