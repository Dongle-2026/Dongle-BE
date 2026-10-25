#!/usr/bin/env bash
# 운영 배포(처음·업데이트 공통). 저장소 루트에서 실행: bash scripts/deploy/deploy.sh
# - .env.deploy가 없으면 비밀값을 무작위로 만들어 생성한다(카카오·구글 키, 도메인은 직접 채운다).
# - 빌드 후 DB·Redis·백엔드·AI 서비스를 띄우고 health를 확인한다.
set -euo pipefail
cd "$(dirname "$0")/../.."

ENV_FILE=.env.deploy
COMPOSE=(docker compose --env-file "$ENV_FILE" -p youthletter-prod -f docker-compose.prod.yml --profile ai)

if [[ ! -f "$ENV_FILE" ]]; then
  cp .env.deploy.example "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  for key in DB_PASSWORD JWT_SECRET AI_SERVICE_TOKEN; do
    value="$(openssl rand -hex 32)"
    sed -i.bak "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  done
  rm -f "$ENV_FILE.bak"
  echo "$ENV_FILE 를 만들었습니다(DB·JWT·AI 비밀값 자동 생성)."
  echo "편집해서 아래를 채운 뒤 다시 실행하세요:"
  echo "  APP_CORS_ALLOWED_ORIGINS, OAUTH2_REDIRECT_URI, KAKAO_CLIENT_ID/SECRET (구글은 쓰면)"
  echo "  nano $ENV_FILE"
  exit 0
fi

if grep -qE 'CHANGE_ME|YOUR_' "$ENV_FILE"; then
  echo "$ENV_FILE 에 채우지 않은 값이 있습니다:" >&2
  grep -nE 'CHANGE_ME|YOUR_' "$ENV_FILE" | cut -d= -f1 >&2
  exit 1
fi

git pull --ff-only || echo "(git pull 생략: 로컬 변경 또는 브랜치 확인 필요)"
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up -d --build --wait --wait-timeout 900

echo "health 확인:"
for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:8080/api/health; then echo; echo "배포 완료"; exit 0; fi
  sleep 5
done
echo "health 응답이 없습니다. 로그: ${COMPOSE[*]} logs --tail=100 backend ai-service" >&2
exit 1
