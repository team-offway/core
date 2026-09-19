#!/usr/bin/env python3
"""역·터미널 좌표를 시·도로 판정한다 — 출발지 자동완성의 시드를 만드는 오프라인 도구(#590).

## 왜 필요한가

출발지 자동완성은 "서울" 을 치면 **서울에 있는** 역·터미널을 보여야 한다. 그런데 이름만으로는
안 된다 — 서울의 주요 역 대부분이 이름에 "서울" 을 담지 않는다(용산·청량리·영등포·왕십리·수서·
광운대). 좌표 사각형으로 자르는 것도 안 된다: 서울 범위로 잡으면 경기 것이 섞여 들어온다
(부천·일산·대곡·행신·능곡·원릉·탄현·퇴계원·사릉·대야). 서울과 경기는 경계가 맞물려 있다.

그래서 **시도 경계로 판정한다.** 판정은 한 번만 하고 결과를 마이그레이션에 박는다 — 역·터미널은
행정구역이 바뀌지 않는 한 소속이 변하지 않고, 요청 경로에서 외부를 부르지 않는다는 규칙 그대로다.

## 왜 역지오코딩 API 를 쓰지 않나

좌표를 주소로 바꿔 주는 외부 API 를 붙이면 의존성과 키가 하나 늘고, 부팅 때 900여 건을 태운다.
경계 데이터는 공개돼 있고 판정은 한 번이면 끝난다 — 그 값에 맞는 비용이 아니다.

## 쓰는 법

좌표는 **DB 가 정본이다.** 시드 파일의 좌표는 이후 보정 마이그레이션이 덮어써서 낡았다
(실측 2026-09-19: 시드 파일 871건 vs DB 905건).

    OFFWAY_DB_HOST=<운영 IP> ./scripts/prod-db.sh \\
      "SELECT 'TRAIN' src, code, name, '' kind, lat, lng FROM train_station WHERE lat IS NOT NULL
       UNION ALL SELECT 'BUS', code, name, kind, lat, lng FROM bus_terminal WHERE lat IS NOT NULL" \\
      > hubs.tsv
    python3 scripts/transit/hub-sido.py < hubs.tsv > hub-sido.tsv

출력은 입력 열에 `sido` 를 붙인 TSV 다. 마이그레이션 SQL 은 이 결과로 만든다.
"""

import csv
import json
import os
import sys
import urllib.request

# 통계청 시도 경계(2018). 행정구역 경계는 거의 변하지 않으므로 연도를 고정한다 — 최신을 따라가면
# 판정 결과가 실행 시점에 따라 달라지고, 그러면 시드를 다시 만들 때마다 diff 가 생긴다.
BOUNDARY_URL = (
    "https://raw.githubusercontent.com/southkorea/southkorea-maps/master"
    "/kostat/2018/json/skorea-provinces-2018-geo.json"
)

# **판정 결과를 지금 표기로 바꾸지 않는다.** 경계 데이터의 이름(2018년 기준)을 그대로 내린다.
#
# 바꾸고 싶은 유혹이 있다 — `region.sido` 는 `전남광주통합특별시` 처럼 통합 이후 표기를 쓴다.
# 그런데 광주를 전남에 합쳐 저장하면 **"광주" 로 검색했을 때 전남 132곳이 전부 걸린다.** 목포·여수
# 터미널이 광주 검색에 뜨는 것은 틀렸다.
#
# 그래서 DB 에는 **판정한 값 그대로**(광주광역시·전라남도·강원도) 넣고, 표시 이름과 검색 토큰은
# `OriginSido` enum 이 소유한다. 측정값과 표기 정책을 한 컬럼에 섞지 않는다.

CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".sido-boundary.json")


def load_boundary():
    """경계를 받아 캐시한다 — 7MB 라 매번 내려받을 이유가 없다."""
    if not os.path.exists(CACHE):
        with urllib.request.urlopen(BOUNDARY_URL, timeout=120) as res:
            data = res.read()
        with open(CACHE, "wb") as f:
            f.write(data)
    with open(CACHE) as f:
        return json.load(f)


def rings(geometry):
    """폴리곤·멀티폴리곤의 바깥 링만 모은다.

    구멍(내부 링)은 버린다 — 시도 경계에 다른 시도를 품는 구멍이 없고, 있어도 섬·호수라
    역·터미널이 놓이지 않는다.
    """
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"][0]]
    return [polygon[0] for polygon in geometry["coordinates"]]


def build_sidos():
    sidos = []
    for feature in load_boundary()["features"]:
        name = feature["properties"]["name"]
        outlines = rings(feature["geometry"])
        xs = [x for ring in outlines for x, _ in ring]
        ys = [y for ring in outlines for _, y in ring]
        sidos.append({
            "name": name,
            "code": feature["properties"]["code"],
            "rings": outlines,
            "bbox": (min(xs), min(ys), max(xs), max(ys)),
        })
    return sidos


def contains(outlines, x, y):
    """ray casting — 점에서 오른쪽으로 반직선을 그어 경계와 만나는 횟수를 센다(홀수면 안)."""
    inside = False
    for ring in outlines:
        count = len(ring)
        for i in range(count):
            x1, y1 = ring[i]
            x2, y2 = ring[(i + 1) % count]
            if (y1 > y) != (y2 > y):
                crossing = x1 + (y - y1) * (x2 - x1) / (y2 - y1)
                if x < crossing:
                    inside = not inside
    return inside


def nearest_sido(sidos, lat, lng):
    """경계 안에 안 들어오는 점을 가장 가까운 시도로 붙인다.

    **필요한 이유**: 간척지·매립지는 2018년 경계 밖에 있다. 서화성역(화성시 서부)이 그렇다.
    그런 점을 미판정으로 남기면 그 허브가 자동완성에서 통째로 사라진다 — 좌표는 있고 실제로
    쓸 수 있는 역인데 목록에 없는 것이 더 나쁘다.

    거리는 링 정점까지의 평면 근사로 잰다. 정확한 점-선분 거리가 아니어도 되는 이유는, 이
    경로를 타는 점이 경계에서 수 km 안쪽에 있어 어느 시도가 가까운지가 뒤집히지 않기 때문이다.
    """
    best, best_d2 = None, None
    for sido in sidos:
        for ring in sido["rings"]:
            for x, y in ring:
                d2 = (x - lng) ** 2 + (y - lat) ** 2
                if best_d2 is None or d2 < best_d2:
                    best, best_d2 = sido, d2
    return best


def main():
    sidos = build_sidos()
    reader = csv.DictReader(sys.stdin, delimiter="\t")
    rows = list(reader)

    writer = csv.DictWriter(
        sys.stdout, fieldnames=list(reader.fieldnames) + ["sido"], delimiter="\t"
    )
    writer.writeheader()

    fallbacks = []
    for row in rows:
        lat, lng = float(row["lat"]), float(row["lng"])
        hit = None
        for sido in sidos:
            x0, y0, x1, y1 = sido["bbox"]
            if x0 <= lng <= x1 and y0 <= lat <= y1 and contains(sido["rings"], lng, lat):
                hit = sido
                break
        if hit is None:
            hit = nearest_sido(sidos, lat, lng)
            fallbacks.append((row.get("name", ""), hit["name"]))
        writer.writerow({**row, "sido": hit["name"]})

    # 경계 밖 판정은 **반드시 눈에 보이게 남긴다.** 조용히 최근접으로 붙이면 엉뚱한 시도에
    # 들어간 허브를 아무도 모른다.
    print(f"판정 {len(rows)}건 · 경계 밖 최근접 {len(fallbacks)}건", file=sys.stderr)
    for name, sido in fallbacks:
        print(f"  경계 밖 → 최근접: {name} = {sido}", file=sys.stderr)


if __name__ == "__main__":
    main()
