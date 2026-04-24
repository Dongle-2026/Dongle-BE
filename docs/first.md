# 1. Docker Desktop 실행
   open -a Docker

   ▎ 상단 메뉴바에 Docker 아이콘이 활성화될 때까지 대기

# 2. DB + Redis 컨테이너 띄우기
   docker compose -f docker-compose.dev.yml up -d

   ▎ docker 명령이 안 되면 심볼릭 링크가 깨져있으므로 전체 경로 사용:
   
▎ /Applications/Docker.app/Contents/Resources/bin/docker compose -f

▎ docker-compose.dev.yml up -d

# 3. Spring Boot 실행
- IntelliJ에서 YouthLetterApplication.java Run
- 또는 터미널: ./gradlew bootRun

# 4. 확인

┌─────────────┬────────────────────────────────────────────────────────────┐
│    항목     │                            URL                             │
├─────────────┼────────────────────────────────────────────────────────────┤
│ 헬스체크    │ http://localhost:8080/actuator/health                      │
├─────────────┼────────────────────────────────────────────────────────────┤
│ Swagger UI  │ http://localhost:8080/swagger-ui/index.html                │
├─────────────┼────────────────────────────────────────────────────────────┤
│ 수동 크롤링 │ curl -X POST http://localhost:8080/api/admin/crawl/trigger │
└─────────────┴────────────────────────────────────────────────────────────┘

# 5. 종료
## 컨테이너 중지
docker compose -f docker-compose.dev.yml down

## 데이터까지 삭제하고 싶을 때 (DB 초기화)
docker compose -f docker-compose.dev.yml down -v

  ---
## docker 심볼릭 링크 수정 (1회만)

현재 /usr/local/bin/docker가 깨진 링크를 가리키고 있어서 매번 전체 경로를 써야
합니다. 한번 고치면 이후 docker 명령을 바로 쓸 수 있습니다:

sudo ln -sf /Applications/Docker.app/Contents/Resources/bin/docker
/usr/local/bin/docker
sudo ln -sf /Applications/Docker.app/Contents/Resources/bin/docker-compose
/usr/local/bin/docker-compose