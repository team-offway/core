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
PICKS = ["공주시", "태안군", "정선군", "의성군", "가평군",
         "강화군", "남해군", "고성군", "영양군", "울릉군"]


def service_key():
    secret = REPO / SECRET_NAME
    if not secret.exists():
        sys.exit(f"시크릿이 없다: {secret}")
    for line in io.open(secret, encoding="utf-8"):
        if line.startswith("DATA_GO_KR_SERVICE_KEY="):
            return line.split("=", 1)[1].strip()
    sys.exit("키 없음")


def regions():
    text = io.open(MIG, encoding="utf-8").read()
    found = re.findall(
        r"area_code=(\d+), sigungu_code=(\d+) WHERE sido='([^']+)' AND sigungu='([^']+)'", text)
    by_name = {name: (int(a), int(s), sido) for a, s, sido, name in found}
    return [(n, *by_name[n]) for n in PICKS if n in by_name]


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
          f"{len(targets) * len(kinds) * REPEATS}콜\n")

    samples = {label: [] for label, _ in kinds}
    sizes = {label: [] for label, _ in kinds}
    fails = {label: 0 for label, _ in kinds}

    # 종류를 번갈아 부른다 — 같은 종류를 연달아 부르면 그 순간의 서버 상태가 한 종류에 몰린다.
    for attempt in range(REPEATS):
        for name, area, sigungu, _sido in targets:
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

    print("\n" + "=" * 64)
    print(f"{'종류':10s} {'n':>4s} {'p50':>7s} {'p95':>7s} {'p99':>7s} {'max':>7s} "
          f"{'평균KB':>7s} {'실패':>4s} {'6초초과':>6s}")
    for label, _ in kinds:
        xs = sorted(samples[label])
        n = len(xs)
        def q(p):
            return xs[min(n - 1, int(round(p * (n - 1))))]
        over = sum(1 for x in xs if x > 6000)
        avg_kb = (statistics.mean(sizes[label]) / 1024) if sizes[label] else 0
        print(f"{label:10s} {n:4d} {q(.50):7.0f} {q(.95):7.0f} {q(.99):7.0f} {xs[-1]:7.0f} "
              f"{avg_kb:7.1f} {fails[label]:4d} {over:6d}")


if __name__ == "__main__":
    main()
