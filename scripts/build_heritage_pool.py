#!/usr/bin/env python3
"""국가유산청 오픈API 에서 우리 89개 지역의 국가유산을 뽑아 배포 파일로 만든다(#160).

**왜 필요한가.** 인허가 데이터로 숙소·맛집은 넉넉해졌지만 볼거리는 여전히 얇다. 그중 상당수가
야영장·골프장이라 "그 지역에서 볼 만한 것" 과 거리가 있다. 국보·보물·사적·천연기념물은 관광 가치가
분명하고, 문화재가 0건인 지역이 없어 얇던 지역이 고르게 채워진다.

**data.go.kr 이 아니다.** 국가유산청 자체 API 라 관광 API 일일 한도와 무관하다 — 그쪽이 말라도 이건 산다.
인증키도 필요 없고 User-Agent 만 있으면 된다.

**좌표가 다 오지는 않는다.** 실측(2026-08-10, 표본 220건)에서 상세를 불러도 latitude 가 0 인 것이 25% 였다.
코스 생성은 좌표가 필수라(동선·군집) 그대로면 그만큼이 후보에서 빠진다. 그래도 **전부 담는다** — 지역
볼거리 수와 상세 화면에는 쓸 수 있고, 주소로 지오코딩을 붙이면 코스 후보로 승격된다.

**그래서 3단계다.** 수집이 비싸서(상세 건당 0.44초) 지오코딩을 따로 떼어 다시 돌릴 수 있게 했다.
  ① 목록(SearchKindOpenapiList.do) — 시도별로 페이지를 돌며 이름·종목·시군구를 받는다. 좌표는 없다.
  ② 상세(SearchKindOpenapiDt.do)   — 건별로 좌표·주소·이미지·설명을 받는다.
  ③ 지오코딩(`--geocode`)          — ②가 남긴 빈 좌표를 주소로 채운다. 카카오 → 실패분만 네이버.

사용:
  python3 scripts/build_heritage_pool.py                       # ①②
  python3 scripts/build_heritage_pool.py --geocode             # ③ (기존 파일을 제자리 갱신)
  python3 scripts/build_heritage_pool.py --limit-per-sido 20 --dry-run   # 표본만 재본다
"""

from __future__ import annotations

import argparse
import collections
import csv
import gzip
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

LIST_URL = "https://www.khs.go.kr/cha/SearchKindOpenapiList.do"
DETAIL_URL = "https://www.khs.go.kr/cha/SearchKindOpenapiDt.do"

# 제공기관이 봇을 막지 않도록 사람이 읽을 수 있는 이름을 남긴다.
USER_AGENT = "Mozilla/5.0 (compatible; OffWay-build/1.0)"

# 우리 89곳이 걸친 시도만 받는다. 서울·대전·울산·제주 등은 인구감소지역이 없어 받을 이유가 없다.
#
# **광주(24)와 전남(36)이 같은 결과를 준다** — 이 API 도 둘을 한 코드로 묶는다(장소 풀의
# `전남광주통합특별시` 와 같은 현상). 우리 89곳에 광주가 없어 전남 하나로 받으면 된다.
TARGET_CTCD = {
    "21": "부산광역시", "22": "대구광역시", "23": "인천광역시", "31": "경기도",
    "32": "강원특별자치도", "33": "충청북도", "34": "충청남도", "35": "전북특별자치도",
    "36": "전라남도", "37": "경상북도", "38": "경상남도",
}

PAGE_UNIT = 100          # 목록 한 페이지 최대
MAX_PAGES = 60           # 폭주 안전장치 — 시도 하나가 6,000건을 넘을 리 없다
# 상세 팬아웃 동시성. **8 로 30분을 밀어붙였더니 제공기관이 연결을 거절했다**(Connection refused).
# 우리 빌드 한 번이 남의 서버를 막을 이유가 없다 — 조금 느려도 3 으로 둔다.
DETAIL_WORKERS = 3
RETRY_ATTEMPTS = 2
RETRY_BACKOFF_SECONDS = 1.0

# 지오코딩 — 좌표가 빠진 것을 주소로 채운다.
#
# **둘을 쓰는 이유.** 국가유산 주소는 `강원특별자치도 평창군 오대산로 1211-14(진부면, 상원사)` 처럼
# 괄호 주기가 붙거나 `경상북도 경주시 진현동 산 1-1` 같은 옛 지번이라, 한쪽이 못 찾는 것이 나온다.
# 카카오를 먼저 쓰고(무료 한도가 넉넉하다) 실패분만 네이버로 넘긴다.
#
# 네이버는 **신 엔드포인트만** 된다 — `naveropenapi.apigw.ntruss.com` 은 신규 구독에 401 을 준다(실측).
KAKAO_URL = "https://dapi.kakao.com/v2/local/search/address.json"
NAVER_URL = "https://maps.apigw.ntruss.com/map-geocode/v2/geocode"
SECRET_FILE = "/Users/sevin/Desktop/git/offway/application-secret.properties"
GEOCODE_WORKERS = 4      # 남의 서버를 밀어붙이지 않는 선. 카카오는 초당 상한이 있다
GEOCODE_TIMEOUT = 10

# 지오코딩에 넘길 최소 주소 토큰 수(시도·시군구·그 아래). 시군구까지만 있으면 관공서 좌표가 나온다.
MIN_ADDRESS_TOKENS = 3

SEED_SQL = "src/main/resources/db/migration/V20260718200440__create_region_and_seed.sql"
OUTPUT = "src/main/resources/data/heritage-pool.csv.gz"

# 상세 응답 원본 캐시(배포 대상 아님). 있으면 재사용해 다시 안 부른다.
#
# **압축하지 않는다.** gzip 은 닫을 때 flush 하므로, 크래시하면 버퍼에 있던 것이 같이 날아간다 —
# 크래시에 대비하려고 둔 캐시가 크래시에 같이 죽으면 의미가 없다. 배포에 들어가지 않는 중간 산출물이라
# 용량을 아낄 이유도 없다.
DETAIL_CACHE = "build/heritage-detail-cache.jsonl"


def fetch(url: str) -> str:
    """XML 을 문자열로. 실패는 짧게 재시도하고, 그래도 안 되면 던진다(빌드가 조용히 반쪽이 되지 않게)."""
    last: Exception | None = None
    for attempt in range(RETRY_ATTEMPTS + 1):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=25) as response:
                return response.read().decode("utf-8", "replace")
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            last = error
            if attempt < RETRY_ATTEMPTS:
                time.sleep(RETRY_BACKOFF_SECONDS * (attempt + 1))
    raise RuntimeError(f"조회 실패: {url}") from last


def tag(xml: str, name: str) -> str:
    """CDATA 를 벗겨 낸 태그 값. 없으면 빈 문자열.

    <b>여는 태그와 CDATA 사이의 공백을 허용해야 한다.</b> 제공기관 XML 은 필드마다 정렬이 다르다 —
    이름·설명은 붙여 쓰는데 소재지(`ccbaLcad`)만 줄바꿈이 들어간다. 공백을 안 봐주면 그 optional 그룹이
    통째로 빗나가 `<![CDATA[...]]>` 를 값으로 들고 온다(실측: 주소 2,641건 전량이 그랬다).
    """
    match = re.search(rf"<{name}>\s*(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?\s*</{name}>", xml, re.S)
    return strip_cdata(match.group(1)) if match else ""


def strip_cdata(value: str) -> str:
    """이미 캐시에 들어간 값도 고칠 수 있게, 벗기기를 따로 둔다."""
    cleaned = re.sub(r"^\s*<!\[CDATA\[(.*?)\]\]>\s*$", r"\1", value, flags=re.S)
    return cleaned.strip()


def seeded_regions() -> dict[tuple[str, str], int]:
    """시드를 읽어 {(시도, 시군구): region_id}. INSERT 순서가 곧 id 라는 것은 장소 풀 스크립트와 같은 전제다."""
    sql = pathlib.Path(SEED_SQL).read_text(encoding="utf-8")
    regions: dict[tuple[str, str], int] = {}
    for index, (sido, sigungu) in enumerate(
            re.findall(r"\('([^']+)','([^']+)','[^']*','[^']*'\)", sql), start=1):
        regions[(sido, sigungu)] = index
    return regions


def list_items(ctcd: str, sido: str) -> list[dict[str, str]]:
    """시도 하나의 목록 전량. 마지막 페이지는 항목이 비어 끝난다.

    <b>시도명을 항목에 박아 둔다.</b> 목록 응답의 {@code ccbaCtcd} 가 비어 오는 항목이 있어, 나중에
    항목 자신의 코드로 시도를 되찾으려 하면 지역을 못 찾는다(실측: 평창군 등에서 KeyError).
    """
    items: list[dict[str, str]] = []
    for page in range(1, MAX_PAGES + 1):
        xml = fetch(f"{LIST_URL}?ccbaCtcd={ctcd}&pageUnit={PAGE_UNIT}&pageIndex={page}&ccbaCncl=N")
        blocks = re.findall(r"<item>(.*?)</item>", xml, re.S)
        if not blocks:
            return items
        for block in blocks:
            items.append({
                "kdcd": tag(block, "ccbaKdcd"),
                "asno": tag(block, "ccbaAsno"),
                "ctcd": tag(block, "ccbaCtcd") or ctcd,
                "sido": sido,
                "name": tag(block, "ccbaMnm1"),
                "kind": tag(block, "ccmaName"),
                "sigungu": tag(block, "ccsiName"),
            })
        if len(blocks) < PAGE_UNIT:
            return items
    # 상한에 걸렸다 — 부분 수집을 그대로 쓰면 그 시도가 조용히 반쪽이 된다.
    raise RuntimeError(f"목록 페이지 상한({MAX_PAGES}) 도달 ctcd={ctcd}")


def detail(item: dict[str, str]) -> dict[str, str]:
    """건별 좌표·주소·이미지·설명. 좌표는 절반가량이 0 으로 온다."""
    xml = fetch(f"{DETAIL_URL}?ccbaKdcd={item['kdcd']}&ccbaAsno={item['asno']}&ccbaCtcd={item['ctcd']}")
    return {
        **item,
        # 대분류(gcodeName)가 "갈 수 있는 곳인가" 를 가른다 — 종목(국보·보물)으로는 못 가른다.
        # 국보 `동궐도`, 보물 `자수 초충도 병풍` 처럼 소장 유물도 같은 종목을 달고 있고, 그 주소는
        # 소장 기관이라 코스 스팟으로 쓰면 "그림 한 점" 이 목적지가 된다.
        "group": tag(xml, "gcodeName"),
        "subgroup": tag(xml, "bcodeName"),
        "lat": tag(xml, "latitude"),
        "lng": tag(xml, "longitude"),
        "address": re.sub(r"\s+", " ", tag(xml, "ccbaLcad")).strip(),
        "imageUrl": tag(xml, "imageUrl"),
        "content": re.sub(r"\s+", " ", tag(xml, "content")).strip(),
    }


def usable_coordinate(value: str) -> bool:
    """0 은 '없음' 이다 — 제공기관이 미상을 0 으로 채워 보낸다."""
    try:
        return abs(float(value)) > 0.0001
    except (TypeError, ValueError):
        return False


# ── ③ 지오코딩 ──────────────────────────────────────────────────────────────

def secrets() -> dict[str, str]:
    """gitignore 된 시크릿 파일에서 키를 읽는다 — 인자로 받으면 셸 히스토리에 남는다."""
    found = {}
    for line in pathlib.Path(SECRET_FILE).read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.strip().startswith("#"):
            key, _, value = line.partition("=")
            found[key.strip()] = value.strip()
    return found


def clean_address(address: str) -> str:
    """괄호 주기를 떼고 '산 1-1' 의 공백을 붙인다 — 둘 다 지오코더가 못 찾는 흔한 형태다."""
    without_note = re.sub(r"[(（][^)）]*[)）]", " ", address)
    joined = re.sub(r"\b산\s+(\d)", r"산\1", without_note)
    return re.sub(r"\s+", " ", joined).strip()


def _get_json(url: str, headers: dict[str, str]) -> dict:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, **headers})
    with urllib.request.urlopen(request, timeout=GEOCODE_TIMEOUT) as response:
        return json.loads(response.read().decode("utf-8"))


def geocode_kakao(address: str, keys: dict[str, str]) -> tuple[str, str, str] | None:
    url = f"{KAKAO_URL}?query={urllib.parse.quote(address)}"
    found = _get_json(url, {"Authorization": f"KakaoAK {keys['KAKAO_REST_API_KEY']}"})
    for doc in found.get("documents", []):
        return doc["y"], doc["x"], doc["address_name"]
    return None


def geocode_naver(address: str, keys: dict[str, str]) -> tuple[str, str, str] | None:
    url = f"{NAVER_URL}?query={urllib.parse.quote(address)}"
    found = _get_json(url, {
        "x-ncp-apigw-api-key-id": keys["NAVER_MAPS_CLIENT_ID"],
        "x-ncp-apigw-api-key": keys["NAVER_MAPS_CLIENT_SECRET"],
    })
    for item in found.get("addresses", []):
        return item["y"], item["x"], item.get("roadAddress") or item.get("jibunAddress", "")
    return None


def geocode(address: str, sigungu: str, keys: dict[str, str]) -> tuple[str, str, str] | None:
    """카카오 → 실패분만 네이버. **시군구가 어긋나면 버린다.**

    지오코더는 못 찾으면 비슷한 이름의 엉뚱한 곳을 자신 있게 준다. 좌표 하나가 다른 시군구로 찍히면
    그 지역 코스에 남의 동네가 끼어 동선이 통째로 무너진다 — 못 채우는 편이 낫다.
    """
    cleaned = clean_address(address)
    if len(cleaned.split()) < MIN_ADDRESS_TOKENS:
        # `부산광역시 동구` 처럼 시군구까지만 있는 주소는 구청 좌표로 찍힌다. 지오코더는 성공했다고 하지만
        # 유산은 엉뚱한 자리에 서고, 그 좌표로 동선을 짜면 코스가 통째로 어긋난다. 못 채우는 편이 낫다.
        return None
    for provider in (geocode_kakao, geocode_naver):
        try:
            hit = provider(cleaned, keys)
        except (urllib.error.URLError, OSError, KeyError, ValueError):
            continue
        if hit and sigungu and sigungu in hit[2]:
            return hit
    return None


def run_geocode() -> int:
    keys = secrets()
    missing = [k for k in ("KAKAO_REST_API_KEY", "NAVER_MAPS_CLIENT_ID", "NAVER_MAPS_CLIENT_SECRET")
               if not keys.get(k)]
    if missing:
        print("시크릿 없음:", ", ".join(missing))
        return 1

    path = pathlib.Path(OUTPUT)
    if not path.exists():
        print(f"{OUTPUT} 이 없습니다 — 먼저 수집을 돌리세요")
        return 1
    with gzip.open(path, "rt", encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))

    # CSV 의 region_id 는 문자열이다. 정수 키로 두면 조회가 전부 빗나가 시군구가 빈 채로 검증에 들어가고,
    # 그러면 채운 좌표가 한 건도 안 남는다(실측: 0/1,753).
    sigungu_of = {str(region_id): sigungu for (_, sigungu), region_id in seeded_regions().items()}
    blank = [r for r in rows if not r["lat"] or not r["lng"]]
    print(f"전체 {len(rows):,}건 중 좌표 없음 {len(blank):,}건 — 지오코딩 시작")

    def fill(row: dict[str, str]) -> bool:
        hit = geocode(row["address"], sigungu_of.get(row["region_id"], ""), keys)
        if not hit:
            return False
        row["lat"], row["lng"] = hit[0], hit[1]
        return True

    with ThreadPoolExecutor(max_workers=GEOCODE_WORKERS) as pool:
        filled = sum(pool.map(fill, blank))
    print(f"채움 {filled:,} / {len(blank):,}건 ({filled * 100 // max(len(blank), 1)}%)")

    with gzip.open(path, "wt", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    have = sum(1 for r in rows if r["lat"] and r["lng"])
    print(f"{OUTPUT} 갱신 — 좌표 보유 {have:,}/{len(rows):,} ({have * 100 // len(rows)}%)")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="국가유산 풀 생성")
    parser.add_argument("--limit-per-sido", type=int, default=0,
                        help="시도당 상세 조회 건수 상한(표본 측정용). 0 이면 전량")
    parser.add_argument("--dry-run", action="store_true", help="파일을 쓰지 않고 집계만 낸다")
    parser.add_argument("--geocode", action="store_true",
                        help="수집은 건너뛰고, 이미 만든 파일의 빈 좌표만 주소로 채운다")
    args = parser.parse_args()

    if args.geocode:
        return run_geocode()

    regions = seeded_regions()
    ours = {sigungu for _, sigungu in regions}

    matched: list[dict[str, str]] = []
    for ctcd, sido in TARGET_CTCD.items():
        items = list_items(ctcd, sido)
        mine = [i for i in items if (sido, i["sigungu"]) in regions]
        if args.limit_per_sido:
            mine = mine[: args.limit_per_sido]
        matched.extend(mine)
        print(f"  {sido:10s} 전체 {len(items):5,} → 우리 지역 {len(mine):5,}", flush=True)
    print(f"\n목록 매칭 합계 {len(matched):,}건")

    # 이미 받아 둔 것은 다시 안 부른다. 상세는 건당 1초에 전량 30분이라, 뒤 단계에서 한 번 죽으면
    # 그게 통째로 날아간다 — 실제로 그렇게 잃었다.
    cache = pathlib.Path(DETAIL_CACHE)
    cached: dict[str, dict[str, str]] = {}
    if cache.exists():
        with cache.open(encoding="utf-8") as f:
            for line in f:
                try:
                    row = json.loads(line)
                except ValueError:
                    continue  # 죽는 순간 반쯤 써진 마지막 줄
                if "group" not in row:
                    continue  # 분류 필드를 늘리기 전에 받은 행 — 다시 받는다
                for field in ("name", "address", "content", "imageUrl", "kind"):
                    row[field] = strip_cdata(row.get(field) or "")
                cached[row["kdcd"] + "/" + row["asno"] + "/" + row["ctcd"]] = row
        print(f"캐시에서 {len(cached):,}건 재사용")

    todo = [i for i in matched if i["kdcd"] + "/" + i["asno"] + "/" + i["ctcd"] not in cached]
    if todo:
        cache.parent.mkdir(parents=True, exist_ok=True)
        done = 0
        with cache.open("a", encoding="utf-8") as sink, \
                ThreadPoolExecutor(max_workers=DETAIL_WORKERS) as pool:
            for row in pool.map(detail, todo):
                sink.write(json.dumps(row, ensure_ascii=False) + "\n")
                sink.flush()   # 줄 단위로 내려야 크래시해도 남는다
                cached[row["kdcd"] + "/" + row["asno"] + "/" + row["ctcd"]] = row
                done += 1
                if done % 500 == 0:
                    print(f"  상세 {done:,}/{len(todo):,}", flush=True)
    detailed = [cached[i["kdcd"] + "/" + i["asno"] + "/" + i["ctcd"]] for i in matched]

    with_coordinate = [d for d in detailed if usable_coordinate(d["lat"]) and usable_coordinate(d["lng"])]
    print(f"상세 조회 {len(detailed):,}건 → 좌표 있음 {len(with_coordinate):,} "
          f"({len(with_coordinate) * 100 // max(len(detailed), 1)}%)")
    print("이미지 있음", sum(1 for d in detailed if d["imageUrl"]))
    print("설명 있음  ", sum(1 for d in detailed if d["content"]))
    print("종목 상위 :", dict(collections.Counter(d["kind"] for d in detailed).most_common(8)))
    print("대분류    :", dict(collections.Counter(d["group"] for d in detailed).most_common()))

    if args.dry_run:
        return 0

    rows = []
    seen: set[tuple] = set()
    duplicates = 0
    for d in detailed:
        natural = (d["sido"], d["sigungu"], d["name"], d["address"])
        if natural in seen:
            duplicates += 1
            continue
        seen.add(natural)
        rows.append((
            regions[(d["sido"], d["sigungu"])],
            d["kind"], d["group"], d["subgroup"], d["name"], d["address"],
            d["lat"] if usable_coordinate(d["lat"]) else "",
            d["lng"] if usable_coordinate(d["lng"]) else "",
            d["imageUrl"], d["content"],
        ))
    if duplicates:
        # 조용히 지나가면 "왜 6,392 가 아니지" 를 나중에 다시 파게 된다.
        print(f"자연키 중복 {duplicates:,}건 제외(지역·이름·소재지가 같은 행)")
    output = pathlib.Path(OUTPUT)
    output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(output, "wt", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["region_id", "kind", "group", "subgroup", "name", "address",
                         "lat", "lng", "image_url", "content"])
        writer.writerows(rows)
    print(f"\n{output} 에 {len(rows):,}건 기록")
    return 0


if __name__ == "__main__":
    sys.exit(main())
