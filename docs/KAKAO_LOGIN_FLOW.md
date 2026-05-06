# 카카오 OAuth2 로그인 연동 가이드

> 백엔드(Spring Boot) ↔ 프론트엔드(Next.js) 간 카카오 로그인 통신 흐름 문서.  
> 연동 문제 발생 시 이 문서의 체크리스트를 순서대로 확인한다.

---

## 1. 전체 플로우

```
Frontend (localhost:3000)          Backend (localhost:8080)             Kakao
       │                                    │                              │
  1. 로그인 버튼 클릭                         │                              │
       │──── window.location.href ──────────►│                              │
       │  /oauth2/authorization/kakao        │                              │
       │                                    │──── 302 redirect ──────────────►│
       │                                    │                     카카오 로그인 페이지
       │                                    │◄─── code=AUTH_CODE ─────────────│
       │                           (콜백: /login/oauth2/code/kakao)          │
       │                                    │                              │
       │                              2. 코드 교환                          │
       │                                    │──── POST /oauth/token ─────────►│
       │                                    │◄─── kakao_access_token ─────────│
       │                                    │──── GET /v2/user/me ───────────►│
       │                                    │◄─── 사용자 정보 (id, email, nickname) ─│
       │                                    │                              │
       │                              3. DB 처리                           │
       │                                    │  OAuthAccount 조회/생성        │
       │                                    │  User 조회/생성                │
       │                                    │                              │
       │                              4. JWT 발급                          │
       │                                    │  createAccessToken()  (15분)  │
       │                                    │  createRefreshToken() (7일)   │
       │                                    │                              │
       │◄── 302 redirect ───────────────────│                              │
  /auth/callback                            │                              │
  ?accessToken=JWT_ACCESS                  │                              │
  &refreshToken=JWT_REFRESH                │                              │
  &nickname=홍길동                          │                              │
       │                                    │                              │
  5. 토큰 저장 및 홈 이동                     │                              │
  localStorage.setItem(...)               │                              │
  router.push('/')                        │                              │
```

---

## 2. 사전 설정

### 카카오 개발자 콘솔

| 항목 | 값 |
|------|-----|
| 플랫폼 > Web > 사이트 도메인 | `http://localhost:8080` |
| Redirect URI | `http://localhost:8080/login/oauth2/code/kakao` |
| 동의항목 | `profile_nickname` (필수), `account_email` (선택 권장) |

> `account_email` 미동의 시 email이 null로 수신되어 임시 이메일(`kakao_{id}@oauth.local`)로 계정이 생성됩니다.

### 백엔드 환경변수 (`.env` 또는 IntelliJ Run Configuration)

```
KAKAO_CLIENT_ID=카카오_앱_REST_API_키
KAKAO_CLIENT_SECRET=카카오_앱_클라이언트_시크릿
```

---

## 3. 백엔드 구성

### 3-1. application.yml 핵심 설정

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            # 실제 값: http://localhost:8080/login/oauth2/code/kakao
            authorization-grant-type: authorization_code
            client-authentication-method: client_secret_post
            scope: profile_nickname, account_email
        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id

oauth2:
  redirect-uri: ${OAUTH2_REDIRECT_URI:http://localhost:3000/auth/callback}
  # 이 URL로 accessToken, refreshToken, nickname을 쿼리 파라미터에 담아 redirect
```

### 3-2. 주요 파일 역할

| 파일 | 역할 |
|------|------|
| `SecurityConfig.java` | OAuth2 로그인 경로 허용, SuccessHandler/FailureHandler 등록 |
| `CustomOAuth2UserService.java` | 카카오에서 받은 사용자 정보로 DB User 조회/생성 |
| `KakaoUserInfo.java` | 카카오 응답 속성(`kakao_account.email`, `properties.nickname`) 파싱 |
| `OAuth2AuthenticationSuccessHandler.java` | JWT 발급 후 프론트로 redirect |
| `OAuth2AuthenticationFailureHandler.java` | 실패 시 에러 메시지를 쿼리 파라미터로 프론트에 전달 |

### 3-3. 사용자 처리 로직 (`CustomOAuth2UserService`)

```
카카오 사용자 정보 수신
       │
       ▼
OAuthAccount(provider=KAKAO, providerId={kakao_id}) 조회
       │
   존재? ──YES──► 기존 User 반환
       │
      NO
       │
       ▼
User(email={kakao_account.email}) 조회
       │
   존재? ──YES──► 기존 User에 OAuthAccount 연결 (계정 연동)
       │
      NO
       │
       ▼
신규 User 생성 + OAuthAccount 생성
```

### 3-4. JWT 발급 (`OAuth2AuthenticationSuccessHandler`)

성공 시 아래 URL로 302 redirect:
```
http://localhost:3000/auth/callback
  ?accessToken={accessToken}   // 유효기간 15분
  &refreshToken={refreshToken} // 유효기간 7일
```

실패 시:
```
http://localhost:3000/auth/callback
  ?error={에러 메시지}
```

---

## 4. 프론트엔드 구성

### 4-1. 로그인 버튼

```typescript
// ❌ 잘못된 방법 — CORS 에러 발생
await axios.get('http://localhost:8080/oauth2/authorization/kakao');

// ✅ 올바른 방법 — 브라우저를 직접 이동
window.location.href = 'http://localhost:8080/oauth2/authorization/kakao';
```

### 4-2. 콜백 페이지 (`/auth/callback`)

```typescript
// app/auth/callback/page.tsx
'use client';

import { useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

export default function AuthCallbackPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const accessToken  = searchParams.get('accessToken');
    const refreshToken = searchParams.get('refreshToken');
    const error        = searchParams.get('error');

    if (error) {
      console.error('OAuth2 로그인 실패:', error);
      router.push('/login?error=' + encodeURIComponent(error));
      return;
    }

    if (accessToken && refreshToken) {
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      router.push('/');
    }
  }, [searchParams, router]);

  return <div>로그인 처리 중...</div>;
}
```

### 4-3. 인증된 API 호출

```typescript
// lib/api.ts — Access Token을 Authorization 헤더에 포함
async function authFetch(url: string, options: RequestInit = {}) {
  const accessToken = localStorage.getItem('accessToken');
  return fetch(`http://localhost:8080${url}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${accessToken}`,
      ...options.headers,
    },
  });
}

// 사용 예
const res = await authFetch('/api/users/me');
```

### 4-4. Access Token 만료 시 재발급

```typescript
// Access Token 만료 → 401 응답 수신 시
async function reissueToken() {
  const refreshToken = localStorage.getItem('refreshToken');

  const res = await fetch('http://localhost:8080/api/auth/reissue', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });

  if (!res.ok) {
    // Refresh Token도 만료 → 재로그인
    localStorage.clear();
    window.location.href = '/login';
    return;
  }

  const { data } = await res.json();
  // data: { accessToken, refreshToken, nickname }
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('refreshToken', data.refreshToken);
}
```

---

## 5. 설정값 요약

| 항목 | 로컬 개발 | 운영 |
|------|-----------|------|
| 백엔드 주소 | `http://localhost:8080` | `https://api.youthletter.com` |
| 프론트 주소 | `http://localhost:3000` | `https://youthletter.com` |
| OAuth2 시작 URL | `/oauth2/authorization/kakao` | 동일 |
| 카카오 Redirect URI | `http://localhost:8080/login/oauth2/code/kakao` | `https://api.youthletter.com/login/oauth2/code/kakao` |
| 프론트 콜백 페이지 | `http://localhost:3000/auth/callback` | `https://youthletter.com/auth/callback` |
| 환경변수 | `OAUTH2_REDIRECT_URI=http://localhost:3000/auth/callback` | `OAUTH2_REDIRECT_URI=https://youthletter.com/auth/callback` |

---

## 6. 문제 해결 체크리스트

### 카카오 인가 화면이 안 뜰 때
- [ ] `KAKAO_CLIENT_ID` 환경변수 설정 여부 확인
- [ ] 카카오 개발자 콘솔에 `http://localhost:8080` 플랫폼 도메인 등록 여부 확인

### 콜백 후 에러가 반환될 때
- [ ] 카카오 개발자 콘솔 Redirect URI에 `http://localhost:8080/login/oauth2/code/kakao` 정확히 등록 여부 확인
- [ ] `KAKAO_CLIENT_SECRET` 값 정확성 확인
- [ ] 카카오 앱 동의항목 설정 확인 (`profile_nickname` 필수)

### 콜백 페이지에서 토큰이 안 올 때
- [ ] `OAUTH2_REDIRECT_URI` 가 `http://localhost:3000/auth/callback` 으로 설정되어 있는지 확인
- [ ] 프론트 Next.js 서버가 포트 `3000`에서 실행 중인지 확인
- [ ] 프론트에 `/auth/callback` 페이지 파일이 존재하는지 확인

### CORS 에러가 날 때
- [ ] 로그인 버튼에서 `fetch` 대신 `window.location.href` 사용 여부 확인 (OAuth2 흐름은 브라우저 redirect 방식)
- [ ] `SecurityConfig`의 CORS 허용 Origin에 `http://localhost:3000` 포함 여부 확인

### 로그인 후 API 호출이 401일 때
- [ ] `Authorization: Bearer {accessToken}` 헤더 포함 여부 확인
- [ ] Access Token 만료(15분) 여부 확인 → `/api/auth/reissue` 로 재발급
