# 구현 및 연결 현황

## 최신 업데이트 — 2026-10-03

현재 React Native 저장소는 /Users/da-eun/Desktop/Dongle-APP이다. 실제 뉴스·검색·카드·로그인·저장·용어 학습·통계 연결, 인증 토큰 분리와 멱등 북마크 API를 구현했다. 정책·소비·알림 화면도 연결 작업을 진행했다. [앱 테스트 전 개발 및 검증](PRE_APP_TEST_2026-10-03.md)이 최신 기록이다. 앱 저장소 docs/PRE_APP_TEST.md에는 실행 설정·실기기 체크리스트가 있다.

아래는 **9/29 당시 기록**이며 저장소 경로 미확인 등은 현재의 차단 사유가 아니다. 원본 DB는 보존하고 별도 복제 DB youthletter_iphone_20260929에 스키마를 적용한 이후 이력이 있다. 10/3 자동 테스트는 테스트 DB 5433에서 수행한다.

## 2026-09-29 기록

## 구현한 항목

| 항목 | 구현·검증 |
|---|---|
| AI 장애 관측 | 요청 ID·안전한 오류 코드·시간·timeout 504 구분. 자동 테스트와 짧은 실모델 진단 |
| 비동기 AI | 관리자 202 접수/조회, DB 큐, 중복 억제, 최대 3회 재시도, lease 복구·오래된 응답 차단. PostgreSQL 테스트 |
| 상태 확인 | DB·Redis 상태에 따른 200/503, 연결 반환, 내부 오류 은닉. 단위 테스트 |
| 소비 데이터 | DB sequence ID, 사용자 행 잠금, 동일 시각 이벤트 ID 정렬, 삭제 후 수정 차단·중복 삭제 멱등성. 단위/DB 테스트 |
| 알림 | 읽음/발송 상태 분리, 가짜 발송 완료 제거, D-7/3/1별 생성, 스케줄 직렬화. 상태 독립성 테스트 |
| CI | 테스트 스키마 보강, Node 계약 테스트 추가. GitHub Actions 실행은 미확인 |

Spring 68건 통과, 실제 모델 통합 1건 조건부 생략. Node 11건 통과 기록. 실제 모바일 완성본 검증과 구분한다.

## 기존 DB 적용

Flyway 자동 실행은 없다. **서비스를 중지하고 백업한 후** 기존 upgrade-capstone.sql 적용 여부를 확인하고 다음 파일을 psql `-v ON_ERROR_STOP=1 -f`로 순서대로 적용한다.

1. `src/main/resources/db/migration/V16__ai_jobs.sql`
2. `src/main/resources/db/migration/V17__spending_record_sequence.sql`
3. `src/main/resources/db/migration/V18__notification_read_state.sql`
4. `src/main/resources/db/migration/V19__term_bookmarks.sql` (용어 저장 테이블, 재실행 안전)
5. `src/main/resources/db/migration/V20__notification_settings.sql` (알림 설정, 재실행 안전)
6. `src/main/resources/db/migration/V21__profile_image.sql` (users.bio/avatar_id, profile_images, 재실행 안전)
7. `src/main/resources/db/migration/V22__search_logs.sql` (검색어 기록·카테고리 인덱스, 재실행 안전). 적용 후 `POST /api/admin/news/reclassify`로 기존 기사 재분류
8. `src/main/resources/db/migration/V23__quiz_honey.sql` (퀴즈·풀이·꿀 원장, ai_jobs kind에 QUIZ 추가, 재실행 안전)
9. `src/main/resources/db/migration/V24__article_thumbnail.sql` (기사 썸네일 URL). 적용 후 `POST /api/admin/news/thumbnails`로 기존 기사 채우기(1회 최대 100건)

이번 작업에서는 테스트 DB(5433)에만 적용했다. 개발 DB(15432)·운영 DB는 미적용이다. V17은 기존 소비 ID보다 큰 값에서 시작하고 번호는 건너뛸 수 있다. V18의 기존 알림은 미읽음으로 시작한다. 기존 is_sent에는 가짜 발송과 사용자 읽음이 혼재하여 읽음 상태를 복원할 근거가 없다. 기존 발송 이력은 삭제하지 않는다.

앱 알림 화면은 새 `isRead`를 사용해야 한다. `isSent`는 읽음 여부가 아니다. 정책 스케줄은 UTC 09:00이며 한국시간 09:00을 원하면 cron을 UTC 00:00으로 설정한다.

## 남은 작업과 필요한 정보

- React Native 앱 API 연결·실기기 빌드: 앱 저장소 경로 필요. Desktop의 Dongle-FE는 Next.js이므로 대신 수정하지 않았다.
- 모바일 OAuth: 앱 scheme/앱 링크와 제공자 콘솔 설정 확인 필요. 기존 URL 토큰 전달을 모바일 완성 방식으로 간주하지 않는다. 일회용 코드·안전한 리다이렉트 교환 설계/구현 및 앱 테스트가 남아 있다.
- 푸시: Firebase/APNs·플랫폼 설정과 앱 토큰 종류 확인 필요. Expo Push Token과 FCM registration token을 혼용하면 안 된다. 발송 어댑터·토큰 갱신·계정전환/로그아웃 정리·실기기 수신은 미완료다. [Firebase 설정](https://firebase.google.com/docs/admin/setup), [공식 발송](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk).
- AI 안정화: 큐는 생성 시간을 줄이지 않는다. 고정 기사 20건 평가, 실제 프로세스 중단·재시작, 모델+큐+DB 전체 테스트 필요. 기존 동기 API·크롤러는 큐 직렬화 대상이 아니므로 동시에 호출하면 Node 429가 발생할 수 있다.
- 배포·완성본: 운영 주소/HTTPS, 백업·스키마 적용, 설치 파일, 시연 영상·발표. 실제 주가/정책 공급원 설정·최신성도 검증해야 한다.

필요한 사용자 정보: **React Native 저장소 경로, 푸시 방식(Firebase 직접/Expo), 로그인 제공자·콘솔 준비 여부, 확정 제출일**. 비밀키는 채팅이나 Git에 올리지 않고 환경변수/보안 파일로 관리한다.
