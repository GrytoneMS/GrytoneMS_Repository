# 배포 (서버 한 대 + Docker)

MySQL, Redis, 백엔드, 프론트(Caddy)를 한 서버에서 Docker로 띄웁니다.
Caddy가 `https://<IP를-로-이은>.sslip.io` 주소로 HTTPS 인증서를 자동으로 받고,
`/api`, `/files`는 백엔드로, 나머지는 프론트 화면으로 보냅니다.

## 처음 한 번

서버(Ubuntu 24.04, 포트 22·80·443 열림)에 SSH로 접속한 뒤:

```bash
git clone -b dev https://github.com/GrytoneMS/GrytoneMS_Repository.git
cd GrytoneMS_Repository/deploy
bash setup.sh          # 스왑, Docker, .env 생성
exit                   # docker 그룹 적용을 위해 다시 접속
```

다시 접속해서:

```bash
cd GrytoneMS_Repository/deploy
docker compose up -d --build     # 첫 빌드는 10분 정도 걸림
```

## 수정사항 반영

dev에 병합된 뒤 서버에서:

```bash
cd GrytoneMS_Repository && git pull && cd deploy && docker compose up -d --build
```

## 자주 쓰는 명령

- 상태: `docker compose ps`
- 백엔드 로그: `docker compose logs -f backend` (메일 설정이 없으면 인증 코드가 여기 찍힘)
- 관리자 지정: `docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" teamup -e "update users set role=\"ADMIN\" where email=\"메일주소\";"'`
- 전체 종료: `docker compose down` (데이터 유지) / `docker compose down -v` (데이터까지 삭제)
