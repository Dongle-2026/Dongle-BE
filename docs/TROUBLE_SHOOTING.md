# TROUBLE_SHOOTING.md — 트러블슈팅 기록

> 발생한 이슈와 해결 과정을 기록합니다.

---

## 2026-04-24

---

### ISSUE-1. Swagger UI 파일 공유 시 CORS 오류

**상황**  
팀원에게 `docs.html` 파일을 직접 전달했더니 아래 오류 발생.

```
Fetch error
Failed to fetch http://localhost:8080/v3/api-docs
Possible cross-origin (CORS) issue? The URL origin (http://localhost:8080)
does not match the page (file://).
```

**원인**  
`docs.html`이 `http://` 서버가 아닌 로컬 파일(`file://`)로 열림.  
브라우저는 `file://` → `http://` 크로스 오리진 요청을 차단한다.  
게다가 팀원 컴퓨터의 `localhost:8080`에는 Spring Boot 앱이 켜져 있지 않았다.

**해결**  
`/v3/api-docs`를 fetch하는 방식 → **OpenAPI spec JSON을 HTML 파일 안에 직접 내장**하는 방식으로 변경.

```python
# 앱 실행 중일 때 spec을 뽑아서 HTML에 embed
curl -s http://localhost:8080/v3/api-docs > /tmp/openapi-spec.json

python3 -c "
import json
spec = json.load(open('/tmp/openapi-spec.json'))
spec_js = json.dumps(spec, ensure_ascii=False)
# spec_js를 HTML의 const SPEC = {...}; 에 삽입
"
```

결과: 인터넷만 되면 `file://`로 열어도 동작하는 단독 HTML 파일 완성.

---

### ISSUE-2. editor.swagger.io "Unable to render editor content" 오류

**상황**  
`docs/openapi.json`을 GitHub에 올리고 아래 URL을 공유했으나 오류 발생.

```
https://editor.swagger.io/?url=https://raw.githubusercontent.com/.../openapi.json

Unable to render editor content
Content was not recognized as supported language in particular format.
```

**원인**  
`editor.swagger.io`는 **OpenAPI 3.1.0을 지원하지 않는다.**  
springdoc-openapi 2.x는 기본적으로 3.1.0 spec을 생성한다.

```json
{ "openapi": "3.1.0", ... }  ← editor.swagger.io 미지원
```

**해결**  
`application.yml`에 버전 다운그레이드 설정 추가.

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
    version: openapi_3_0   # ← 이 줄 추가
```

앱 재시작 후 spec 버전이 `3.0.1`로 변경됨.  
이후 `openapi.json` 재생성 → push.

---

### ISSUE-3. editor.swagger.io URL 파라미터 방식 여전히 실패

**상황**  
3.0.1로 다운그레이드 후에도 동일 오류 반복.

**원인**  
`editor.swagger.io/?url=<GitHub raw URL>` 방식은 editor.swagger.io 서버가 GitHub raw URL을 직접 fetch한다.  
GitHub raw URL이 CORS 헤더를 올바르게 반환해도 editor.swagger.io 쪽 네트워크 정책 또는 파싱 이슈로 로드 실패하는 경우가 있다.

**해결 과정**

| 시도 | 결과 |
|------|------|
| GitHub raw URL → editor.swagger.io | ❌ 실패 |
| GitHub Pages 설정 시도 | ❌ 조직 설정으로 비활성화됨 (ISSUE-4 참고) |
| `docs/index.html` 생성 (spec 내장) + surge.sh 배포 | ✅ 성공 |

**최종 해결**  
`docs/index.html`에 spec을 내장한 Swagger UI CDN 파일 생성 후 **surge.sh**로 배포.

```bash
npx surge /tmp/youthletter-docs --domain youthletter-api.surge.sh
```

→ `https://youthletter-api.surge.sh` 로 팀원 공유 가능.

---

### ISSUE-4. GitHub Pages 조직 설정으로 비활성화

**상황**  
GitHub 레포 Settings → Pages 진입 시 아래 메시지.

```
Pages on this repository are disabled.
Please contact your organization administrators.
```

**원인**  
`Dongle-2026` 조직의 관리자가 GitHub Pages를 조직 전체에서 비활성화해 둔 상태.  
개별 레포에서 설정 불가능하며, 조직 Owner만 해제할 수 있다.

**해결**  
GitHub Pages 대신 surge.sh 사용 (ISSUE-3 해결 참고).

---

### ISSUE-5. main 머지 후 working directory 파일 대량 삭제

**상황**  
`feature/test-ci` → `main` 머지 작업 중 다수 소스 파일이 삭제된 상태로 표시.  
빌드 시 아래 오류 발생.

```
error: package com.youthletter.global.auth.jwt does not exist
import com.youthletter.global.auth.jwt.JwtAuthenticationFilter;
```

**git status 확인 결과**
```
D src/main/java/com/youthletter/global/auth/jwt/JwtAuthenticationFilter.java
D src/main/java/com/youthletter/global/auth/jwt/JwtTokenProvider.java
D src/main/java/com/youthletter/global/auth/jwt/CustomUserPrincipal.java
D src/main/java/com/youthletter/domain/crawler/service/HankyungCrawlerService.java
... (다수)
```

**원인**  
아래 git 작업 순서에서 stash pop이 working directory를 꼬이게 만듦.

```
1. feature/test-ci에서 git stash        ← SecurityConfig.java 변경사항 임시 저장
2. git checkout main
3. git merge feature/test-ci            ← main에 머지 (성공)
4. git pull origin main (충돌 해결)
5. git push origin main
6. git stash pop                        ← ⚠️ feature/test-ci 파일들 일부 삭제됨
7. git checkout feature/test-ci
```

`stash pop` 시점에 HEAD가 main인 상태에서 feature 브랜치 파일들이 "삭제 대상"으로 인식된 것으로 추정.

**해결**  
`feature/test-ci`로 돌아온 후 HEAD 기준으로 전체 working directory 복구.

```bash
git checkout HEAD -- .
```

이후 빌드 정상 확인.

**재발 방지**  
- 브랜치 전환 전 `git stash`보다 작업 중인 변경사항을 **커밋** 후 이동하는 것이 안전.
- 어쩔 수 없이 stash를 써야 한다면, pop 후 `git status`로 삭제된 파일 없는지 즉시 확인.

---

## 요약

| 이슈 | 원인 | 해결 |
|------|------|------|
| CORS on file:// | file:// → http:// 요청 차단 | spec을 HTML에 내장 |
| editor.swagger.io 렌더링 실패 | OpenAPI 3.1.0 미지원 | springdoc `version: openapi_3_0` 설정 |
| URL 파라미터 방식 실패 | editor.swagger.io 네트워크/파싱 이슈 | surge.sh로 직접 호스팅 |
| GitHub Pages 비활성화 | 조직 관리자 설정 | surge.sh 대체 |
| working directory 파일 삭제 | stash pop 타이밍 이슈 | `git checkout HEAD -- .` |
