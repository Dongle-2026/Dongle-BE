# 로컬 풀스택 연동 가이드

> BE(Dongle-BE) + FE(Dongle-FE) 동시 실행 방법  
> 작성: 정다은 / 최초 작성: 2026-05-04

---

## 전제 조건

| 도구 | 필요 버전 | 확인 명령 |
|------|-----------|-----------|
| Docker Desktop | 최신 | `docker --version` |
| Node.js | 18 이상 | `node --version` |
| npm | 9 이상 | `npm --version` |
| Git | 최신 | `git --version` |

---

## 디렉터리 구조

```
~/Desktop/
├── youthletter-backend/   ← Dongle-BE (현재 레포)
└── Dongle-FE/             ← Dongle-FE (아래에서 클론)
```

---

## 최초 1회 셋업

### Step 1 — Dongle-FE 클론

```bash
cd ~/Desktop
git clone --branch dev https://github.com/Dongle-2026/Dongle-FE.git
cd Dongle-FE
npm install
```

### Step 2 — FE 환경 변수 파일 생성

```bash
# ~/Desktop/Dongle-FE/.env.local
cat > .env.local <<'EOF'
# 백엔드 API 서버 주소
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080

# OAuth2 콜백 (소셜 로그인 후 FE로 리다이렉트)
NEXT_PUBLIC_OAUTH_CALLBACK_URL=http://localhost:3000/auth/callback
EOF
```

> `.env.local`은 `.gitignore`에 포함되어 커밋되지 않습니다.

---

## 매번 실행 순서

### 터미널 A — BE (Docker: PostgreSQL + Redis + Spring Boot)

```bash
cd ~/Desktop/youthletter-backend

# 최초 또는 BE 코드 변경 시
docker-compose -f docker-compose.local.yml up -d --build

# 코드 변경 없을 때 (이미지 재사용)
docker-compose -f docker-compose.local.yml up -d

# 상태 확인
docker-compose -f docker-compose.local.yml ps
```

BE 준비 확인:
```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"} 이면 정상
```

### 터미널 B — FE (Next.js)

```bash
cd ~/Desktop/Dongle-FE

# FE dev 서버 실행 (포트 3000)
npm run dev
```

브라우저에서 `http://localhost:3000` 접속.

---

## 포트 구성

| 서비스 | 포트 | 접속 주소 |
|--------|------|-----------|
| Spring Boot API | 8080 | http://localhost:8080 |
| PostgreSQL | 5432 | localhost:5432 (youthletter_user) |
| Redis | 6379 | localhost:6379 |
| Next.js FE | 3000 | http://localhost:3000 |
| Swagger UI | 8080 | http://localhost:8080/swagger-ui.html |

---

## FE → BE API 호출 패턴

FE 코드에서 API를 호출할 때 아래 패턴을 사용합니다.

```typescript
// src/lib/api.ts
const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export async function fetchJson<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) throw new Error(`API error ${res.status}`);
  return res.json();
}

// 사용 예시
const news = await fetchJson<NewsListResponse[]>('/api/news');
const term = await fetchJson<TermDetailResponse>(`/api/terms/${termId}`);
```

---

## FE dev 브랜치 최신화

```bash
cd ~/Desktop/Dongle-FE
git fetch origin
git pull origin dev
npm install   # package.json 변경 시만
```

---

## BE 코드 변경 후 반영

```bash
cd ~/Desktop/youthletter-backend

# 코드 수정 후 재빌드 + 재시작
docker-compose -f docker-compose.local.yml up -d --build api
```

`--build api`는 api 서비스만 재빌드해서 속도가 빠릅니다 (PostgreSQL, Redis는 건드리지 않음).

---

## DB 초기화 (스키마 리셋)

스키마가 엉킨 경우 볼륨째 삭제 후 재시작합니다.

```bash
cd ~/Desktop/youthletter-backend
docker-compose -f docker-compose.local.yml down -v
docker-compose -f docker-compose.local.yml up -d --build
```

> `-v` 플래그가 볼륨(postgres_data, redis_data)을 같이 삭제합니다.  
> `init.sql`이 다시 적용되면서 시드 데이터(용어 10건, 정책 3건, 테스트 유저 2명)가 재투입됩니다.

---

## 종료

```bash
# BE 종료 (PostgreSQL, Redis도 함께)
cd ~/Desktop/youthletter-backend
docker-compose -f docker-compose.local.yml down

# FE는 터미널에서 Ctrl+C
```

---

## CORS 설정 현황

`SecurityConfig.java`에서 아래 origin을 허용하고 있습니다.

```java
config.addAllowedOrigin("http://localhost:3001");
config.addAllowedOrigin("http://localhost:3000");
config.addAllowedOrigin("http://localhost:8080");
```

FE가 `localhost:3000`에서 실행되므로 별도 설정 불필요합니다.

---

## Dockerfile Apple Silicon (M1/M2) 주의사항

`gradle:8.12-jdk21` + `eclipse-temurin:21-jre-alpine` 조합은 arm64에서 동작하지 않습니다.  
아래 조합이 arm64에서 검증된 버전입니다.

```dockerfile
FROM gradle:8.12-jdk17 AS build      # JDK 17 (build.gradle toolchain과 일치)
FROM eclipse-temurin:17-jre           # Ubuntu 기반, arm64 지원
```

Ubuntu 기반이므로 유저 생성 명령어도 Alpine과 다릅니다.

```dockerfile
# Alpine 문법 (❌ arm64 ubuntu에서 오류)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Ubuntu/Debian 문법 (✅)
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
```

---

## 트러블슈팅

### BE가 뜨지 않는다
```bash
docker-compose -f docker-compose.local.yml logs api | tail -30
```
→ `Caused by: org.postgresql.util.PSQLException` 이면 PostgreSQL이 아직 준비 중. 잠시 기다리면 자동 재시작.

### FE에서 API 호출 시 CORS 에러
→ `SecurityConfig.java`의 `addAllowedOrigin`에 FE 포트 추가 후 BE 재빌드.

### DB 스키마 검증 오류 (`validate` 실패)
→ `docker-compose down -v && up --build`로 DB 초기화.

### 포트 충돌 (`address already in use`)
```bash
lsof -i :8080   # 8080 사용 중인 프로세스 확인
kill -9 <PID>
```
