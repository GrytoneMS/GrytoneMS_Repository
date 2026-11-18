#!/usr/bin/env bash
# 새 Ubuntu 서버에서 한 번만 실행: 스왑 2GB, Docker 설치, .env 생성
# 사용법: deploy 폴더에서  bash setup.sh
set -euo pipefail
cd "$(dirname "$0")"

if ! swapon --show | grep -q /swapfile; then
  echo "== 스왑 2GB 추가"
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

if ! command -v docker >/dev/null; then
  echo "== Docker 설치"
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

sudo timedatectl set-timezone Asia/Seoul || true

if [ ! -f .env ]; then
  echo "== .env 생성"
  IP=$(curl -fsS https://checkip.amazonaws.com | tr -d '[:space:]')
  rand() { openssl rand -hex "$1"; }
  cat > .env <<ENV
DOMAIN=${IP//./-}.sslip.io
DB_PASSWORD=$(rand 16)
DB_ROOT_PASSWORD=$(rand 16)
JWT_SECRET=$(rand 32)
SPRING_MAIL_HOST=
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
MAIL_FROM=
ENV
  chmod 600 .env
fi

echo
echo "준비 끝. 주소: https://$(grep ^DOMAIN= .env | cut -d= -f2)"
echo "다음: 로그아웃 후 다시 접속 → cd $(pwd) && docker compose up -d --build"
