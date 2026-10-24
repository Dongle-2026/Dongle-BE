#!/usr/bin/env bash
# Ubuntu 서버(Oracle Cloud ARM 등) 최초 1회 설정: Docker, Ollama(+모델), Caddy(HTTPS), 방화벽 80/443.
# 사용법: sudo bash scripts/deploy/server-setup.sh api.example.duckdns.org
# 여러 번 실행해도 안전하다(이미 설치된 것은 건너뜀).
set -euo pipefail

DOMAIN="${1:-}"
if [[ -z "$DOMAIN" ]]; then
  echo "사용법: sudo bash scripts/deploy/server-setup.sh <도메인>   예) youthletter.duckdns.org" >&2
  exit 2
fi
if [[ "$(id -u)" -ne 0 ]]; then
  echo "sudo로 실행하세요." >&2
  exit 2
fi
APP_USER="${SUDO_USER:-ubuntu}"
CHAT_MODEL="${OLLAMA_CHAT_MODEL:-gemma3:4b}"
EMBED_MODEL="${OLLAMA_EMBED_MODEL:-nomic-embed-text}"

echo "[1/5] 기본 패키지"
apt-get update -y
apt-get install -y curl git python3 ca-certificates gnupg debian-keyring debian-archive-keyring apt-transport-https

echo "[2/5] Docker"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi
usermod -aG docker "$APP_USER"
systemctl enable --now docker

echo "[3/5] 방화벽: 80/443 열기 (Oracle Ubuntu는 서버 안 iptables도 막혀 있다)"
if command -v iptables >/dev/null; then
  for port in 80 443; do
    iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -p tcp --dport "$port" -j ACCEPT
  done
  if command -v netfilter-persistent >/dev/null; then netfilter-persistent save; fi
fi

echo "[4/5] Ollama + 모델(${CHAT_MODEL}, ${EMBED_MODEL})"
if ! command -v ollama >/dev/null; then
  curl -fsSL https://ollama.com/install.sh | sh
fi
# Docker 컨테이너(ai-service)가 host.docker.internal로 접속할 수 있게 모든 인터페이스에서 받는다.
# 11434 포트는 3단계에서 열지 않았고 클라우드 보안 규칙에도 없으므로 외부에서는 접속할 수 없다.
mkdir -p /etc/systemd/system/ollama.service.d
printf '[Service]\nEnvironment="OLLAMA_HOST=0.0.0.0"\n' > /etc/systemd/system/ollama.service.d/override.conf
systemctl daemon-reload
systemctl enable ollama
systemctl restart ollama
for i in $(seq 1 30); do curl -sf http://127.0.0.1:11434/api/tags >/dev/null && break; sleep 2; done
ollama pull "$CHAT_MODEL"
ollama pull "$EMBED_MODEL"

echo "[5/5] Caddy(HTTPS 자동 발급) → 127.0.0.1:8080"
if ! command -v caddy >/dev/null; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y
  apt-get install -y caddy
fi
cat > /etc/caddy/Caddyfile <<EOF
${DOMAIN} {
    reverse_proxy 127.0.0.1:8080
}
EOF
systemctl enable --now caddy
systemctl reload caddy

echo
echo "서버 준비 완료. 다음: 로그아웃 후 다시 접속(docker 권한 적용) → 저장소 폴더에서 bash scripts/deploy/deploy.sh"
