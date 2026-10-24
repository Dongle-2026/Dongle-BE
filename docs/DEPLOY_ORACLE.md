# Oracle Cloud 무료 서버에 배포하기

처음 배포하는 사람 기준. 서버 1대(ARM 4코어·24GB, Always Free)에 DB·백엔드·AI 서비스·Ollama·Caddy(HTTPS)를 모두 올린다.

## 1. 직접 하는 것 (웹 화면)

1. **Oracle Cloud 가입** — cloud.oracle.com. Home Region은 `South Korea Central (Seoul)` 또는 `South Korea North (Chuncheon)`(나중에 못 바꿈). 카드는 본인 확인용.
2. **서버 만들기** — Compute → Instances → Create instance
   - Image: Canonical Ubuntu 22.04 / Shape: Ampere `VM.Standard.A1.Flex`, OCPU 4, Memory 24GB
   - SSH keys: "Generate a key pair for me" → **Save private key**(잃어버리면 접속 불가)
   - Boot volume 100GB → Create. "Out of capacity"면 다른 Availability Domain 또는 시간 두고 재시도
   - 만들어지면 **Public IP** 메모
3. **포트 열기** — 인스턴스 → Subnet → Security List → Add Ingress Rules: Source `0.0.0.0/0`, TCP `80`, `443`
4. **무료 주소** — duckdns.org 로그인 → 이름 입력(예: `youthletter`) → current ip에 서버 IP → update ip
5. **카카오 개발자 콘솔** — 카카오 로그인 Redirect URI에 `https://<도메인>/login/oauth2/code/kakao` 추가

## 2. 서버 설정 (맥 터미널)

```sh
chmod 600 ~/Downloads/ssh-key-*.key
ssh -i ~/Downloads/ssh-key-*.key ubuntu@<서버IP>
```

서버 안에서:

```sh
git clone -b develop https://github.com/Dongle-2026/Dongle-BE.git youthletter-backend && cd youthletter-backend
sudo bash scripts/deploy/server-setup.sh <도메인>     # Docker·Ollama(+모델)·Caddy·방화벽. 10~20분
exit                                                   # docker 권한 적용을 위해 다시 접속
```

## 3. 배포

```sh
cd youthletter-backend
bash scripts/deploy/deploy.sh      # 첫 실행: .env.deploy 생성(DB·JWT·AI 비밀값 자동)
nano .env.deploy                   # APP_CORS_ALLOWED_ORIGINS, OAUTH2_REDIRECT_URI, KAKAO_CLIENT_ID/SECRET 채우기
bash scripts/deploy/deploy.sh      # 빌드·실행·health 확인(첫 빌드 10분 안팎)
```

- `OAUTH2_REDIRECT_URI`: 로그인 후 앱/프론트로 돌아갈 주소(Expo면 `exp://…/--/oauth/kakao`, 웹이면 `https://프론트/auth/callback`)
- 브라우저에서 `https://<도메인>/` → 기능 확인 페이지. 프론트 API 주소는 `https://<도메인>`
- 업데이트: 같은 폴더에서 `bash scripts/deploy/deploy.sh`(git pull 후 재빌드)

## 4. 맥에서 모은 기사 옮기기

맥(저장소 폴더):

```sh
python3 scripts/db_archive.py --compose-file docker-compose.pipeline.yml --project youthletter-pipeline --env-file .env.pipeline.local backup backups/pipeline-content.dump
scp -i ~/Downloads/ssh-key-*.key backups/pipeline-content.dump ubuntu@<서버IP>:~/youthletter-backend/backups/
```

서버:

```sh
python3 scripts/db_archive.py --compose-file docker-compose.prod.yml --project youthletter-prod --env-file .env.deploy restore backups/pipeline-content.dump --new-database pipeline_import
python3 scripts/import_content.py --source-container youthletter-postgres --source-db pipeline_import --source-user youthletter --target-container youthletter-postgres --target-db youthletter --target-user youthletter --dry-run
```

개수를 확인했으면 `--dry-run` 없이 한 번 더. 이후 서버가 한경 1시간·매일경제 30분마다 직접 수집한다.

## 참고

- 8080은 서버 자신에게만 열려 있고, 외부는 Caddy(443)로만 들어온다. 백엔드는 Caddy가 보내는 `X-Forwarded-Proto/Host`를 읽어 OAuth 콜백을 `https://<도메인>`으로 만든다.
- 로그: `docker compose --env-file .env.deploy -p youthletter-prod -f docker-compose.prod.yml logs --tail=100 backend ai-service`
- `down -v`는 DB를 지운다. 쓰지 않는다.
- Oracle은 오래 놀고 있는 무료 서버를 회수할 수 있다. 걱정되면 계정을 Pay As You Go로 올린다(무료 범위 안에서는 과금 없음).
