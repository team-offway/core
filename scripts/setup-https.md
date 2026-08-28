# api.offway.cloud 에 HTTPS 붙이기 (#232)

운영 API 가 평문 HTTP 로 인터넷에 열려 있었다. `http://18.181.168.227:8080` 이 그대로 응답해,
**access 토큰(Bearer)과 Basic 자격증명이 평문으로 오간다.** 같은 네트워크에 있는 누구든 토큰을 주워
남의 계정으로 API 를 부를 수 있다.

앞단에 Caddy 를 두어 443 에서 TLS 를 끊고, 앱 포트를 인터넷에서 걷어낸다.

## 순서를 지킨다 — 뒤집으면 서비스가 끊긴다

지금 앱은 EC2 의 8080 을 **직접** 부른다. 그래서 8080 을 먼저 닫거나 앱을 먼저 로컬 바인딩으로
바꾸면 그 순간 서비스가 죽는다. 아래 순서는 **어느 시점에도 서비스가 살아 있도록** 짜여 있다.

| # | 할 일 | 누가 | 이 시점의 서비스 |
|---|---|---|---|
| 1 | DNS `api A → 18.181.168.227` | 가비아 | 8080 으로 정상 |
| 2 | SG 인바운드에 **80·443** 추가 | AWS 콘솔 | 8080 으로 정상 |
| 3 | Caddy 기동 (아래) | EC2 | **8080·443 둘 다** 응답 |
| 4 | 프론트가 `API_BASE_URL` 을 `https://api.offway.cloud` 로 재빌드·배포 | 프론트 | 443 으로 옮겨감 |
| 5 | SG 인바운드에서 **8080 제거** | AWS 콘솔 | 443 으로만 |
| 6 | `deploy.yml`·`recover-container.sh` 를 `-p 127.0.0.1:8080:8080` 으로 | PR | 443 으로만 |

**1~4 는 완료.** `https://api.offway.cloud/api/v1/categories` 가 401 로 답하는 것을 확인했다
(401 이 정상이다 — 인증 게이트가 살아 있다는 뜻이다).

**6 은 이 PR 에 들어 있다.** 남은 것은 **5(AWS 콘솔)** 뿐이고, 순서는 5 → 6 이어도 6 → 5 여도 된다 —
둘 다 이미 443 으로 오는 트래픽을 건드리지 않는다.

> **6 이 두 파일인 이유.** 복구 스크립트도 컨테이너를 다시 띄운다. 배포만 고치면 장애가 나서
> 복구가 도는 순간 8080 이 인터넷에 다시 열리는데, 하필 그때는 아무도 포트를 안 본다.

4 를 건너뛰고 5 로 가면 옛 앱을 쓰는 사용자가 전부 끊긴다. 스토어 배포가 퍼지는 시간을 감안해
**4 와 5 사이는 넉넉히 둔다.**

## 3) Caddy 기동

EC2 에 접속해서 아래를 그대로 붙여넣는다.

```bash
mkdir -p ~/offway/caddy
cd ~/offway/caddy
```

`Caddyfile` 을 만든다 — 이 레포의 `scripts/Caddyfile` 과 같은 내용이다.

```bash
cat > Caddyfile <<'EOF'
api.offway.cloud {
	tls sevin@offway.cloud

	reverse_proxy offway-core:8080 {
		header_up X-Real-IP {remote_host}
	}

	header {
		Strict-Transport-Security "max-age=31536000"
		-Server
	}

	log {
		output file /var/log/caddy/access.log {
			roll_size 50MiB
			roll_keep 5
		}
	}
}
EOF
```

띄운다.

```bash
docker run -d \
  --name offway-caddy \
  --network offway-net \
  --restart unless-stopped \
  -p 80:80 -p 443:443 \
  -v ~/offway/caddy/Caddyfile:/etc/caddy/Caddyfile:ro \
  -v caddy-data:/data \
  -v caddy-config:/config \
  -v ~/offway/caddy/log:/var/log/caddy \
  caddy:2-alpine
```

**`caddy-data` 볼륨이 핵심이다.** 발급받은 인증서가 거기 산다. 볼륨 없이 띄우면 컨테이너를 갈아끼울
때마다 새로 발급받고, Let's Encrypt 는 **같은 도메인에 주 5회** 제한이 있어 곧 막힌다. 막히면
일주일을 기다려야 한다.

`offway-net` 에 붙이는 것도 중요하다 — 그래야 `offway-core:8080` 을 컨테이너 이름으로 부른다.
호스트 포트를 거치지 않으므로 6단계에서 앱을 로컬 바인딩으로 바꿔도 이 경로는 그대로 산다.

## 확인

발급에 10~30초 걸린다. 로그에 `certificate obtained successfully` 가 뜨면 된 것이다.

```bash
docker logs -f offway-caddy   # Ctrl-C 로 빠져나온다
```

밖에서 확인한다. **401 이 정상이다** — 인증 게이트가 살아 있다는 뜻이다(#122).

```bash
curl -i https://api.offway.cloud/api/v1/categories
```

인증서를 직접 본다.

```bash
echo | openssl s_client -connect api.offway.cloud:443 -servername api.offway.cloud 2>/dev/null \
  | openssl x509 -noout -issuer -dates
```

## 안 될 때

**`certificate obtained` 가 안 뜬다** — 80 이 SG 에서 열려 있는지 먼저 본다. Let's Encrypt 가
`http://api.offway.cloud/.well-known/acme-challenge/...` 로 소유를 확인하는데 그 경로가 80 으로 온다.

**502 가 온다** — Caddy 는 떴는데 앱을 못 찾는 것이다. 둘이 같은 네트워크인지 본다.

```bash
docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' offway-caddy offway-core
```

## 갱신 실패를 어떻게 아나

Caddy 는 만료 30일 전부터 갱신을 시도하고, **실패해도 옛 인증서로 계속 서비스한다.** 그래서 조용히
지나가다 만료일에 한꺼번에 죽는 것이 이 구성의 유일한 위험이다.

지금은 `tls` 에 적은 주소로 Let's Encrypt 가 만료 임박 메일을 보낸다. 그것이 1차 방어다.

**6 단계에서 배포에 확인을 붙였다.** `deploy.yml` 의 `TLS 인증서 만료 확인` 이 매 배포마다 러너에서
공개 도메인의 인증서를 직접 읽는다 — 서버 안 상태가 아니라 **사용자가 실제로 받는 그 인증서**다.

| 남은 기간 | 배포 |
|---|---|
| 22일 이상 | 조용히 통과 |
| 8~21일 | `::warning::` — 갱신이 안 되고 있을 수 있다 |
| 7일 이하 | **실패** — 갱신이 3주 넘게 실패한 것이라 손이 필요하다 |

이 스텝은 교체·스모크가 **끝난 뒤**에 온다. 그래서 여기서 실패해도 배포 자체는 이미 끝났다 —
"이번 배포가 잘못됐다" 가 아니라 **"인증서를 지금 손봐야 한다"** 는 신호다.

배포가 한동안 없으면 이 확인도 안 돈다. 그때는 메일이 유일한 방어라, 수동 확인은 위 `openssl`
한 줄이면 된다.
