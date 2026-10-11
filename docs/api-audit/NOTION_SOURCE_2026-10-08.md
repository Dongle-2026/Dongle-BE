# API 명세 요청 문서

Date: October 8, 2026
분야: 프론트
status: 진행 중

[전체 API 목록 ](API%20%EB%AA%85%EC%84%B8%20%EC%9A%94%EC%B2%AD%20%EB%AC%B8%EC%84%9C/%EC%A0%84%EC%B2%B4%20API%20%EB%AA%A9%EB%A1%9D%203f35e415ec6880708028e4df773f9613.csv)

- **문서 요약**
    
    ### 공통 규약
    
    - **Base / 인증:** 경로는 코드 주석의 `/api/...`를 유지합니다. A1·A2를 제외한 모든 API는 `Authorization: Bearer <accessToken>` 필수입니다.
    - **응답 envelope:** 현재 `ApiResponse<T>`와 같은 `{ "data": T, "status": 200 }`를 유지하고, **목록 API에만** `"meta"`를 추가합니다.
    - **페이지네이션:** `?page=1&limit=10`(page는 1부터). `meta: { "page": 1, "limit": 10, "hasNext": true }`. 프론트가 지금 `data.length === 10`으로 다음 페이지를 추측하고 있어 `hasNext`가 반드시 필요합니다.
    - **날짜/시간:** ISO 8601 + 오프셋(`2026-10-08T09:30:00+09:00`). 활동 날짜만 `YYYY-MM-DD`(KST 기준 일자).
    - **id:** 모든 리소스 id는 문자열이며 **모든 API에서 같은 체계**를 씁니다. mock에는 `news-12`, `r1`, `t1`, `1`이 섞여 있어 상세 조회가 깨진 적이 있습니다.
    - **에러:** `{ "error": "QUIZ_ALREADY_ANSWERED", "status": 409, "message": "..." }` (`ApiError`). 401이면 프론트가 A2로 갱신 후 1회 재시도합니다.
    - **멱등성:** save / unsave / read / answer는 같은 요청을 반복해도 같은 결과여야 합니다(화면 연타, 재시도 대응).
    
    | 구분 | Method · Path | 용도 | 우선순위 |
    | --- | --- | --- | --- |
    | A1 | `POST /api/auth/oauth/{provider}` | 소셜 로그인 | P0 |
    | A2 | `POST /api/auth/refresh` | 토큰 갱신 | P0 |
    | A3 | `POST /api/auth/logout` | 로그아웃 | P0 |
    | A4 | `GET /api/news` | 뉴스 목록(홈) | P0 |
    | A5 | `GET /api/news/{id}` | 뉴스 상세(헤더 정보) | P0 |
    | A10 | `GET /api/news/{id}/body` | 본문·용어·퀴즈·내 퀴즈 결과 | P0 |
    | A6 | `POST /api/news/{id}/save` | 뉴스 저장 | P0 |
    | A7 | `POST /api/news/{id}/unsave` | 뉴스 저장 해제 | P0 |
    | A14 | `POST /api/news/{id}/read` | 읽음 기록 (제안: 프론트 미구현) | P0 |
    | A11 | `POST /api/quizzes/{quizId}/answer` | 퀴즈 제출·꿀 지급 | P0 |
    | A12 | `POST /api/terms/{id}/save` | 용어 저장 | P0 |
    | A13 | `POST /api/terms/{id}/unsave` | 용어 저장 해제 | P0 |
    | A8 | `GET /api/me` | 내 프로필 | P0 |
    | A9 | `GET /api/me/stats` | 연속 학습·오늘 완료 여부·누적 통계 | P0 |
    | A16 | `GET /api/me/saved-terms` | 저장한 용어 | P0 |
    | A17 | `GET /api/me/saved-news` | 저장한 기사 | P0 |
    | A18 | `GET /api/me/read-history` | 최근 읽은 기사 | P0 |
    | A15 | `GET /api/news/search` | 키워드/검색어 뉴스 목록 | P1 |
    | A20 | `GET /api/categories` | 카테고리 목록 | P1 |
    | A21 | `GET /api/keywords` | 검색 탭 키워드 카드 | P1 |
    | A19 | `GET /api/me/activity` | 히트맵용 날짜별 꿀 | P1 |
    | A25 | `PATCH /api/me/profile` | 프로필 수정(multipart) | P1 |
    | A26 | `GET /api/me/notification-settings` | 알림 설정 조회 | P1 |
    | A27 | `PATCH /api/me/notification-settings` | 알림 설정 변경 | P1 |
    | A28 | `DELETE /api/me` | 회원 탈퇴 (제안: 코드에 없음, 스토어 정책상 검토) | P1 |
    | A22 | `GET /api/me/insights` | 관심/더 알아볼 분야 | P2 |
    | A23 | `GET /api/search/trending` | 실시간 트렌드 | P2 |
    | A24 | `GET /api/search/suggestions` | 자동완성 | P2 |
    | A29 | `POST /api/me/devices` | 푸시 토큰 등록 (제안: 알림 설정이 실제로 동작하려면 필요) | P2 |

### 백엔드와 확정이 필요한 정책 질문

1. **"오늘 학습 완료"의 조건은? →**  기사 1개 읽기인지, 퀴즈 1문제 이상인지. 일 경계는 KST 자정으로 할지
2. **꿀 지급 규칙:** 퀴즈 정답일때 각 문제당 몇 p씩 지급할지
3. **오답 후 재도전**이 가능한지(현재 UI는 불가, 서버도 불가가 자연스러움).
4. **관심/더 알아볼 분야 산출 기준:**
 예) 많이 읽은 카테고리 상위 3개 / 카테고리별 오답률 상위 2개. 퀴즈→기사→카테고리 관계가 필요합니다.
5. **카테고리에 어떤걸 넣을지 확정짓기**, `isNew` 기준.
6. **본문 용어 매칭:** 한 용어가 여러 번 나올 때, 조사가 붙는 경우("로컬크리에이터는")의 하이라이트 규칙. 
현재는 프론트 문자열 매칭입니다.
