"""TourAPI areaBasedList2 응답시간 분포 실측 (#238).

앱과 **같은 요청**을 보낸다 — arrange=B, numOfRows=100, contentTypeId 는 null/39/32.
(TourApiClientImpl.findByArea + RegionPoiService.CANDIDATE_ROWS 와 일치)

표본을 코드에 못박는다: 10지역 × 3종류 × 4회 = 120콜. 일일 한도의 12% 다.
규약이 "실측 전에 남은 한도를 가늠하고 표본 수를 먼저 정한다" 고 하므로 인자로 열지 않는다.

키는 변수에만 담고 절대 출력하지 않는다.
"""
import io
import json
import pathlib
import re
import ssl
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# 레포 루트의 gitignored 시크릿. 키는 변수에만 담고 절대 출력하지 않는다.
SECRET_NAME = "application-secret.properties"
REPO = pathlib.Path(__file__).resolve().parent.parent
MIG = REPO / "src/main/resources/db/migration/V20260724112101__add_region_geo_and_tour_codes.sql"

BASE = "https://apis.data.go.kr/B551011/KorService2/areaBasedList2"
REPEATS = 4
ROWS = 100          # RegionPoiService.CANDIDATE_ROWS
TIMEOUT_S = 30      # 앱의 6초보다 길게 — 꼬리를 잘라내면 분포를 못 본다

# 콘텐츠가 많은 곳과 적은 곳을 섞는다(이슈 요구).
#
# (시도, 시군구) 로 든다 — 시군구 이름만으로는 지역이 갈리지 않는다. 고성군은 강원(32:2)과
# 경남(36:3) 둘 다 있어서, 이름만 키로 쓰면 뒤 행이 앞 행을 덮는다. 어느 고성군을 쟀는지
# 모르면 같은 조건으로 다시 잴 수 없고, 다시 못 재는 숫자는 근거가 못 된다.
PICKS = [
    ("충청남도", "공주시"),
    ("충청남도", "태안군"),
    ("강원특별자치도", "정선군"),
    ("경상북도", "의성군"),
    ("경기도", "가평군"),
    ("인천광역시", "강화군"),
    ("경상남도", "남해군"),
    ("경상남도", "고성군"),
    ("경상북도", "영양군"),
    ("경상북도", "울릉군"),
]


def secret_file():
    """시크릿 파일을 찾는다 — <b>워크트리에서 돌려도 찾아야 한다</b>.

    워크트리는 `<메인체크아웃>/.claude/worktrees/<이름>` 이라 스크립트 기준 레포 루트가 메인이 아니다.
    시크릿은 gitignored 라 워크트리에는 복사되지 않으므로, 없으면 상위로 거슬러 올라가 찾는다.
    """
    for base in [REPO, *REPO.parents]:
        candidate = base / SECRET_NAME
        if candidate.exists():
            return candidate
    return None


def secret_value(name):
    """gitignored 시크릿에서 한 줄을 읽는다. 값은 돌려주기만 하고 어디에도 찍지 않는다."""
    secret = secret_file()
    if secret is None:
        return None
    for line in io.open(secret, encoding="utf-8"):
        if line.startswith(f"{name}="):
            return line.split("=", 1)[1].strip()
    return None


def service_key():
    key = secret_value("DATA_GO_KR_SERVICE_KEY")
    if not key:
        sys.exit(f"DATA_GO_KR_SERVICE_KEY 가 없다 (찾은 시크릿: {secret_file() or SECRET_NAME})")
    return key


def report(text):
    """소비한 한도를 팀 채널에 남긴다 — <b>보고 실패가 측정을 버리게 두지 않는다</b>.

    실측은 사용자 몫과 같은 일일 한도를 태우는데, 그 사실이 돌린 사람 터미널에만 남으면 팀은
    "오늘 왜 한도가 줄었는지" 를 나중에 되짚을 수 없다.
    """
    url = secret_value("DISCORD_MEASURE_WEBHOOK_URL")
    if not url:
        print("\n(웹훅 미설정 — 보고를 건너뛴다)")
        return
    payload = json.dumps({"content": text}).encode("utf-8")
    # User-Agent 를 명시한다 — 파이썬 기본 UA 는 디스코드 앞단 Cloudflare 가 403(error code 1010)으로 막는다.
    request = urllib.request.Request(url, data=payload, headers={
        "Content-Type": "application/json",
        "User-Agent": "offway-measure (+https://github.com/team-offway/core, #238)",
    })
    try:
        with urllib.request.urlopen(request, timeout=10) as resp:
            print(f"\n디스코드 보고 완료 (HTTP {resp.status})")
    except urllib.error.HTTPError as e:
        # 응답 status·본문만 남긴다. e.url 에는 토큰이 있으므로 예외 객체를 그대로 찍지 않는다.
        print(f"\n디스코드 보고 실패 (HTTP {e.code}) {e.read().decode('utf-8', 'replace')[:200]}"
              " — 측정 결과는 위에 그대로 있다")
    except Exception as e:  # noqa: BLE001
        # URL 은 찍지 않는다 — 예외 메시지에 섞여 나올 수 있어 클래스명만 남긴다.
        print(f"\n디스코드 보고 실패 ({type(e).__name__}) — 측정 결과는 위에 그대로 있다")


def regions():
    """PICKS 를 마이그레이션의 areaCode·sigunguCode 로 옮긴다.

    코드를 여기 박지 않는 이유 — 마이그레이션과 두 곳이 되면 어긋나고, 어긋난 코드로 잰 숫자는
    우리 코드의 근거가 못 된다. 워크플로(diagnose.yml)도 이 함수를 그대로 불러 같은 대상을 잰다.
    """
    text = io.open(MIG, encoding="utf-8").read()
    found = re.findall(
        r"area_code=(\d+), sigungu_code=(\d+) WHERE sido='([^']+)' AND sigungu='([^']+)'", text)
    by_region = {(sido, name): (int(a), int(s)) for a, s, sido, name in found}
    return [(sido, name, *by_region[(sido, name)]) for sido, name in PICKS
            if (sido, name) in by_region]


def call(key, area, sigungu, content_type):
    params = {
        "MobileOS": "ETC", "MobileApp": "offway", "_type": "json",
        "arrange": "B", "areaCode": area, "sigunguCode": sigungu, "numOfRows": ROWS,
    }
    if content_type is not None:
        params["contentTypeId"] = content_type
    url = f"{BASE}?{urllib.parse.urlencode(params)}&serviceKey={key}"
    ctx = ssl.create_default_context()
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(url, timeout=TIMEOUT_S, context=ctx) as resp:
            body = resp.read()
        elapsed = (time.perf_counter() - started) * 1000
        total = None
        try:
            total = json.loads(body)["response"]["body"].get("totalCount")
        except Exception:
            pass
        return elapsed, len(body), total, None
    except Exception as e:  # noqa: BLE001 - 무엇이 나오든 분포에 담는다
        return (time.perf_counter() - started) * 1000, 0, None, type(e).__name__


def main():
    key = service_key()
    targets = regions()
    kinds = [("전체타입", None), ("맛집(39)", 39), ("숙박(32)", 32)]
    print(f"표본: {len(targets)}지역 × {len(kinds)}종류 × {REPEATS}회 = "
          f"{len(targets) * len(kinds) * REPEATS}콜")
    # 어느 지역을 쟀는지 시도까지 남긴다 — 같은 이름의 시군구가 있어 이름만으로는 재현이 안 된다.
    for sido, name, area, sigungu in targets:
        print(f"  {sido} {name} ({area}:{sigungu})")
    print()

    samples = {label: [] for label, _ in kinds}
    sizes = {label: [] for label, _ in kinds}
    fails = {label: 0 for label, _ in kinds}

    # 종류를 번갈아 부른다 — 같은 종류를 연달아 부르면 그 순간의 서버 상태가 한 종류에 몰린다.
    for attempt in range(REPEATS):
        for _sido, name, area, sigungu in targets:
            for label, ctype in kinds:
                ms, size, total, err = call(key, area, sigungu, ctype)
                samples[label].append(ms)
                if err:
                    fails[label] += 1
                else:
                    sizes[label].append(size)
                flag = f" !{err}" if err else ""
                print(f"  {attempt+1} {name:5s} {label:9s} {ms:7.0f}ms "
                      f"{size/1024:6.1f}KB total={total}{flag}")

    header = (f"{'종류':10s} {'n':>4s} {'p50':>7s} {'p95':>7s} {'p99':>7s} {'max':>7s} "
              f"{'평균KB':>7s} {'실패':>4s} {'6초초과':>6s}")
    rows = []
    for label, _ in kinds:
        xs = sorted(samples[label])
        n = len(xs)

        def q(p, xs=xs, n=n):
            return xs[min(n - 1, int(round(p * (n - 1))))]

        over = sum(1 for x in xs if x > 6000)
        avg_kb = (statistics.mean(sizes[label]) / 1024) if sizes[label] else 0
        rows.append(f"{label:10s} {n:4d} {q(.50):7.0f} {q(.95):7.0f} {q(.99):7.0f} {xs[-1]:7.0f} "
                    f"{avg_kb:7.1f} {fails[label]:4d} {over:6d}")

    print("\n" + "=" * 64)
    print(header)
    for row in rows:
        print(row)

    calls = len(targets) * len(kinds) * REPEATS
    report("\n".join([
        f"**TourAPI 실측** — 국문관광정보 **{calls}콜** 소비 (일일 한도 1,000 의 {calls / 10:.0f}%)",
        f"표본: {len(targets)}지역 × {len(kinds)}종류 × {REPEATS}회 · `areaBasedList2`",
        "```",
        header,
        *rows,
        "```",
        "표본이 작으면 p99 는 사실상 최댓값이다 — 이 값만으로 timeout 상수를 정하지 않는다.",
    ]))


if __name__ == "__main__":
    main()
