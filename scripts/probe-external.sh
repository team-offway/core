#!/bin/sh
# 우리가 쓰는 외부 API 를 한 번씩 쏘고 정상·느림·무응답으로 가른다.
#
# 왜 있나 (2026-09-06): 열차 조회가 통째로 실패했는데 우리 코드·키·네트워크는 멀쩡했다. `apis.data.go.kr`
# 게이트웨이가 TLS 악수에서 8~10초를 먹고 있었고, 우리 상한은 6초였다. 그 사실을 아는 데 시간이 걸린 이유는
# **"우리 문제인가 저쪽 문제인가" 를 가를 표가 없었기 때문**이다.
#
# 이 표 한 장이 그걸 가른다 — data.go.kr 이 전부 무응답인데 TMAP·카카오·구글·애플이 0.1초에 답하면
# 우리 네트워크·배포·키를 의심할 시간을 안 쓴다. **비교군이 있어야 증명이 된다.**
#
# ── 앱 안에 넣지 않은 이유 ────────────────────────────────────────────────
# 앱은 `ExternalApiHealth`(#474)로 **이미 하고 있는 호출의 결과**를 본다. 그게 공짜이면서 사용자가 실제로
# 겪는 것과 같다. 반면 이 스크립트처럼 주기적으로 쏘면 그만큼 한도를 태운다 — 관광정보는 하루 1,000이라
# 10분 간격 헬스체크만으로 432를 먹는다. 그래서 능동 프로브는 **사람이 필요할 때 부르는 도구**로 둔다.
#
# ── 사용 ─────────────────────────────────────────────────────────────────
#   DATA_GO_KR_KEY=... ./scripts/probe-external.sh          # 재고 디스코드로 보낸다
#   DATA_GO_KR_KEY=... NO_SEND=1 ./scripts/probe-external.sh # 화면에만
#
#   DISCORD_WEBHOOK 이 있으면 embed 로 보낸다. 없으면 화면 출력까지만.
#   키는 절대 인자로 넘기지 않는다 — `ps` 에 그대로 보인다.
set -u

: "${DATA_GO_KR_KEY:?DATA_GO_KR_KEY 환경변수가 필요합니다 (공공데이터포털 일반 인증키)}"
KEY="$DATA_GO_KR_KEY"
WEBHOOK="${DISCORD_WEBHOOK:-}"

TOMORROW=$(date -v+1d +%Y%m%d 2>/dev/null || date -d tomorrow +%Y%m%d)
YESTERDAY=$(date -v-1d +%Y%m%d 2>/dev/null || date -d yesterday +%Y%m%d)
MONTH=$(date +%Y%m)

# 무응답 판정 상한. 우리 클라이언트보다 넉넉히 둔다 — "느림" 과 "무응답" 을 가르려면 기다려 봐야 안다.
TIMEOUT=25
# 요청 경로 클라이언트들의 응답 상한. 이보다 느리면 실제로는 실패로 잡히므로 "느림" 으로 표시한다.
OUR_TIMEOUT=6

# label|group|기대코드|url
# 키가 없어도 호스트가 살아 있으면 401/403 을 빨리 준다 — 그 자체가 "붙는다" 는 증거라 200 과 같이 친다.
probe() {
  label=$1; group=$2; expect=$3; url=$4
  out=$(curl -s --max-time "$TIMEOUT" -o /dev/null \
        -w '%{time_appconnect} %{time_total} %{http_code}' "$url" 2>/dev/null)
  set -- $out
  tls=$1; total=$2; code=$3
  if [ "$code" = "000" ]; then
    verdict="DOWN"
  elif echo "$expect" | grep -q "$code"; then
    verdict=$(awk -v t="$total" -v o="$OUR_TIMEOUT" 'BEGIN{print (t+0>o) ? "SLOW" : "OK"}')
  else
    verdict="ODD"
  fi
  printf '%s|%s|%s|%s|%s|%s\n' "$label" "$group" "$tls" "$total" "$code" "$verdict"
}

RESULTS=$(
  # ── 공공데이터포털 — 우리가 실제로 부르는 엔드포인트만. 전부 apis.data.go.kr 한 곳이다 ──
  probe "TAGO 열차"       "data.go.kr" "200" "https://apis.data.go.kr/1613000/TrainInfo/GetStrtpntAlocFndTrainInfo?serviceKey=${KEY}&depPlaceId=NAT010000&arrPlaceId=NAT014445&depPlandTime=${TOMORROW}&numOfRows=1&_type=json"
  probe "TAGO 시외버스"   "data.go.kr" "200" "https://apis.data.go.kr/1613000/SuburbsBusInfo/GetSuberbsBusTrminlList?serviceKey=${KEY}&numOfRows=1&_type=json"
  probe "TAGO 고속버스"   "data.go.kr" "200" "https://apis.data.go.kr/1613000/ExpBusInfo/GetExpBusTrminlList?serviceKey=${KEY}&numOfRows=1&_type=json"
  probe "TAGO 여객선"     "data.go.kr" "200" "https://apis.data.go.kr/1613000/DmstcShipNvgInfo/GetPortList?serviceKey=${KEY}&numOfRows=1&_type=json"
  probe "TAGO 정류소"     "data.go.kr" "200" "https://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCtyCodeList?serviceKey=${KEY}&numOfRows=1&_type=json"
  probe "국문관광정보"     "data.go.kr" "200" "https://apis.data.go.kr/B551011/KorService2/areaBasedList2?serviceKey=${KEY}&MobileOS=ETC&MobileApp=offway&_type=json&numOfRows=1&areaCode=38&sigunguCode=18"
  probe "관광사진갤러리"   "data.go.kr" "200" "https://apis.data.go.kr/B551011/PhotoGalleryService1/gallerySearchList1?serviceKey=${KEY}&MobileOS=ETC&MobileApp=offway&_type=json&numOfRows=1&keyword=%EC%99%84%EB%8F%84"
  probe "관광빅데이터"     "data.go.kr" "200" "https://apis.data.go.kr/B551011/DataLabService/metcoRegnVisitrDDList?serviceKey=${KEY}&MobileOS=ETC&MobileApp=offway&_type=json&numOfRows=1&startYmd=${MONTH}01&endYmd=${MONTH}01"
  probe "특일정보"        "data.go.kr" "200" "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo?serviceKey=${KEY}&solYear=$(date +%Y)&solMonth=$(date +%m)&_type=json"
  probe "기상청 예보"      "data.go.kr" "200" "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst?serviceKey=${KEY}&dataType=JSON&numOfRows=1&pageNo=1&base_date=${YESTERDAY}&base_time=2300&nx=60&ny=127"

  # ── 비교군 — 다른 호스트들. 이쪽이 멀쩡하면 우리 네트워크가 아니다 ──
  probe "TMAP 경로"       "비교군"     "401 403" "https://apis.openapi.sk.com/tmap/routes?version=1"
  probe "카카오 인증"      "비교군"     "400 401 200" "https://kapi.kakao.com/v2/user/me"
  probe "구글 인증서"      "비교군"     "200" "https://www.googleapis.com/oauth2/v3/certs"
  probe "애플 인증서"      "비교군"     "200" "https://appleid.apple.com/auth/keys"
  probe "관광 이미지 CDN"  "비교군"     "200 302 403 404" "https://ktostay.visitkorea.or.kr/thumb.jpg"
)

# 표는 python 으로 그린다 — awk 의 %-16s 는 바이트를 세서 한글 라벨에서 열이 어긋난다.
python3 - "$RESULTS" <<'PY'
import sys, unicodedata
def width(t):  # 한글·기호는 두 칸을 먹는다
    return sum(2 if unicodedata.east_asian_width(c) in "WF" else 1 for c in t)
def pad(t, n):
    return t + " " * max(0, n - width(t))
rows = [r.split("|") for r in sys.argv[1].strip().splitlines() if r.strip()]
print(f"{pad('API', 18)}{pad('묶음', 12)}{'TLS':>8}{'총':>8}{'HTTP':>7}  판정")
for label, group, tls, total, code, verdict in rows:
    print(f"{pad(label, 18)}{pad(group, 12)}{float(tls):7.1f}s{float(total):7.1f}s{code:>7}  {verdict}")
PY

[ "${NO_SEND:-0}" = "1" ] && exit 0
[ -z "$WEBHOOK" ] && { echo "(DISCORD_WEBHOOK 없음 — 전송 생략)"; exit 0; }

python3 - "$WEBHOOK" "$RESULTS" "$OUR_TIMEOUT" <<'PY'
import json, subprocess, sys, datetime, tempfile, os
webhook, raw, our_timeout = sys.argv[1], sys.argv[2], sys.argv[3]
rows = [r.split("|") for r in raw.strip().splitlines() if r.strip()]
MARK = {"OK": "🟢", "SLOW": "🟡", "DOWN": "🔴", "ODD": "🟠"}
groups = {}
for label, group, tls, total, code, verdict in rows:
    groups.setdefault(group, []).append(
        f"{MARK.get(verdict, '?')} `{label:<14}` {float(total):5.1f}s  HTTP {code}")
counts = {}
for *_, verdict in rows:
    counts[verdict] = counts.get(verdict, 0) + 1
down = counts.get("DOWN", 0) + counts.get("ODD", 0)
slow = counts.get("SLOW", 0)
title = ("외부 API 점검 — 전부 정상" if down == 0 and slow == 0
         else f"외부 API 점검 — 실패 {down} · 느림 {slow} / {len(rows)}")
body = {"embeds": [{
    "title": title,
    "color": 1225983 if down == 0 and slow == 0 else (15158332 if down else 16098851),
    "fields": [{"name": g, "value": "\n".join(v), "inline": False} for g, v in groups.items()],
    "footer": {"text": f"🟢 정상 · 🟡 {our_timeout}초 초과(우리 timeout) · 🔴 무응답 · 🟠 예상 밖 코드"
                       f"   |   {datetime.datetime.now():%m-%d %H:%M}"},
}]}
# 웹훅 URL 이 곧 시크릿이라 본문을 파일로 넘긴다 — 인자로 주면 ps 에 남는다.
fd, path = tempfile.mkstemp(suffix=".json")
try:
    with os.fdopen(fd, "w") as f:
        json.dump(body, f, ensure_ascii=False)
    # python urllib 은 기본 User-Agent 로 403 을 받는다 — curl 로 보낸다.
    code = subprocess.run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", "-X", "POST", webhook,
                           "-H", "Content-Type: application/json", "--data", "@" + path],
                          capture_output=True, text=True).stdout
finally:
    os.unlink(path)
print(f"디스코드 전송 HTTP {code}")
PY
