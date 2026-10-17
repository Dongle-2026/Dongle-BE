# 3차 추가 개발·검증 기록

## 구현

- `GET /api/me/content-reports`: 본인 신고만 조회, 상태 필터·Page·최신순, 사용자 ID 미노출. 일반 사용자는 다른 사용자 신고 및 관리자 처리에 접근 불가.
- `local,sandbox` 실행 프로필: 격리 DB15434/Redis16381/API18081, validate, AI stub, 작업자·크롤러·정책 알림·푸시 기본 해제.
- `scripts/db_archive.py`: 비공개 권한의 custom archive 생성, 파일 덮어쓰기 거부, 실패한 백업 제거. 복원은 새 DB만 생성하고 단일 트랜잭션. 기존 DB명 거부, 실패 DB 자동 삭제 없음.
- AI 서비스 Node22 Dockerfile 및 Compose 선택 profile `ai`. backend에 AI 주소·토큰·모델·작업자·모바일 OAuth·푸시 설정 전달. DB/Redis 호스트 포트는 loopback, 운영 Hibernate는 validate.
- `.env.deploy.example`, 비밀값·백업 Git/Docker 제외 규칙, 상세 실행·테스트·배포 안내.

## 실제 검증

검증은 `/private/tmp/youthletter-quick` 사본과 격리 DB에서 수행. 기존 사용자 변경 파일은 기준 해시 확인 후 선택 반영한다.

| 항목 | 결과 |
|---|---|
| `DB_PORT=15434 REDIS_PORT=16381 ./gradlew test bootJar --no-daemon` | 162개 중 152 통과, 10 조건부 제외, 실패/오류 0. 새 내 신고 테스트 포함 |
| Python unittest | 13 통과(백업 실패·덮어쓰기·기존 DB 거부·원자 복원 명령 포함) |
| 문서의 JAR + local,sandbox | 실제 18081 서버 기동 성공. 호스트 JVM은 Java21, Gradle toolchain은 Java17 |
| 샘플 SQL 실행·실제 회원가입·진단 도구 | health/뉴스2종/본문2종/stats/activity/insights 8 PASS, 새 내 신고 API HTTP200 |
| 실제 백업·새 DB 복원 | next_restore_rehearsal 생성·복원 성공. users2/news0/reports0 확인(샘플 적재 이전 스냅샷) |
| AI Dockerfile | `youthletter-ai:next-verify` 이미지 빌드 성공, npm ci 성공 |
| 운영 Compose | 예제 env + ai profile config --quiet 성공 |

첫 백업 시도는 회귀 테스트 실행 중 DB 연결 한도에 걸렸으며, 부분 파일 제거 후 테스트 종료 뒤 재시도하여 성공했다. 샌드박스의 Gradle 캐시·로컬 네트워크 제한은 승인된 실행으로 재검증했다.

10개 제외는 LiveAi 2, NotionAudit 7, 선택적 JVM 재시작 1이다. 재시작 시험은 이전 차수에서 통과했고 이번 실행에서는 켜지 않았다. AI 비즈니스 소스는 이번에 변경하지 않았으며 이전 21개 통과 기록과 구분한다.

## 남은 범위

운영 배포/HTTPS·실제 서버 500 재현·앱 신고 화면·실기기 OAuth/푸시·AI20건 사실 검수·정책/주가 공급원 확정은 남아 있다. AI 이미지 빌드는 실모델 연동 또는 운영 Compose 전체 기동을 증명하지 않는다. 프로덕션 볼륨에는 접근·마이그레이션하지 않았다.

전체 사용법은 [LOCAL_TEST_AND_DEPLOY.md](LOCAL_TEST_AND_DEPLOY.md), 과제 상태는 [FOLLOWUP_WORK_2026-10-13.md](FOLLOWUP_WORK_2026-10-13.md).
