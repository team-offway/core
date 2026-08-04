#!/usr/bin/env python3
"""LOCALDATA 인허가 ZIP → 장소 풀 CSV(gzip) 생성 (#144).

인구감소지역 89곳의 숙박·음식·볼거리를 하나의 CSV 로 모아
`src/main/resources/data/place-pool.csv.gz` 로 떨군다. 앱은 부팅 시 이 파일만 읽는다.

왜 빌드 타임에 가공하나
  - 원본은 시도별 ZIP 16개 870MB 다. 앱이 이걸 매번 받거나 파싱할 이유가 없다.
  - 좌표 변환(EPSG:5174 → WGS84)에 투영 라이브러리가 필요하다. 런타임 의존을 늘리지 않는다.
  - 분기 단위로만 갱신되는 레퍼런스 데이터라, 파일을 교체하는 편이 자연스럽다.

사용법
  1) 시도별 ZIP 을 내려받는다 (16개, orgCode 는 ORG_CODES 참고)
       curl -L -H 'Referer: https://file.localdata.go.kr/' \
         'https://file.localdata.go.kr/file/download-all?orgCode=6470000_ALL' -o zips/6470000.zip
  2) python3 scripts/build_place_pool.py --zips zips --out src/main/resources/data/place-pool.csv.gz

의존성: pyproj (빌드 타임에만 필요)
"""

from __future__ import annotations  # 파이썬 3.9 에서도 `int | None` 표기를 쓰기 위해

import argparse
import collections
import csv
import gzip
import io
import pathlib
import re
import struct
import sys
import zipfile

# 시도별 다운로드 코드 — `file.localdata.go.kr/file/download-all?orgCode=<코드>_ALL`
ORG_CODES = [
    "6110000",  # 서울
    "6260000",  # 부산
    "6270000",  # 대구
    "6280000",  # 인천
    "6130000",  # 광주
    "6300000",  # 대전
    "6310000",  # 울산
    "5690000",  # 세종
    "6410000",  # 경기
    "6530000",  # 강원
    "6430000",  # 충북
    "6440000",  # 충남
    "6540000",  # 전북
    "6470000",  # 경북
    "6480000",  # 경남
    "6500000",  # 제주
]

# 업종 파일 → (슬롯 종류, 세부 분류). 슬롯 종류는 SlotKind 와 같은 이름을 쓴다.
# 같은 (지역·종류·상호·주소) 가 여럿일 때 남길 우선순위. 낮을수록 먼저 남는다.
# 한옥·사찰처럼 그 자체가 관광 콘텐츠인 분류를 앞에 둔다 — LicensedPlace.Fitness 와 같은 서열이다.
CATEGORY_PRIORITY = {
    "HANOK": 0, "TOURIST_HOTEL": 0, "TOURIST_PENSION": 0, "TOURIST_RESTAURANT": 0,
    "TEMPLE": 0, "MUSEUM": 0, "THEME_PARK": 0, "CABLE_CAR": 0,
    "KOREAN": 0, "SEAFOOD": 0, "TRADITIONAL_TEA": 0,
    "RURAL_HOMESTAY": 1, "CITY_HOMESTAY": 1, "RESTAURANT": 1, "BAKERY": 1,
    "RESORT": 1, "CAMPGROUND": 1, "THEATER": 1, "CULTURE_CENTER": 1, "SKI": 1,
    "GLOBAL": 1, "NOODLE": 1, "BUFFET": 1, "COFFEE": 1, "DESSERT": 1,
    "LODGING": 2, "GOLF": 2, "FASTFOOD": 2, "TEAROOM": 2,
}

# 음식점·휴게음식점은 파일 하나에 온갖 업태가 섞여 있다. 그대로 실으면 여행 코스에 호프집·편의점이
# 배치되므로 `업태구분명` 으로 갈라 분류하고, 여행지 목록에 낼 수 없는 것은 통째로 뺀다.
#
# 값은 89곳 영업중 전수 분포에서 확인한 것이다(일반음식점 113,149건 · 휴게음식점 29,725건).
FOOD_BY_UPTAE = {
    "한식": ("FOOD", "KOREAN"),
    "식육(숯불구이)": ("FOOD", "KOREAN"),
    "횟집": ("FOOD", "SEAFOOD"),
    "복어취급": ("FOOD", "SEAFOOD"),
    "중국식": ("FOOD", "GLOBAL"),
    "일식": ("FOOD", "GLOBAL"),
    "경양식": ("FOOD", "GLOBAL"),
    "패밀리레스트랑": ("FOOD", "GLOBAL"),
    "외국음식전문점(인도,태국등)": ("FOOD", "GLOBAL"),
    "냉면집": ("FOOD", "NOODLE"),
    "분식": ("FOOD", "NOODLE"),
    "김밥(도시락)": ("FOOD", "NOODLE"),
    "뷔페식": ("FOOD", "BUFFET"),
    "기타": ("FOOD", "RESTAURANT"),
    "통닭(치킨)": ("FOOD", "FASTFOOD"),
    "패스트푸드": ("FOOD", "FASTFOOD"),
    "까페": ("CAFE", "COFFEE"),
}

CAFE_BY_UPTAE = {
    "커피숍": ("CAFE", "COFFEE"),
    "전통찻집": ("CAFE", "TRADITIONAL_TEA"),
    "다방": ("CAFE", "TEAROOM"),
    "아이스크림": ("CAFE", "DESSERT"),
    "떡카페": ("CAFE", "DESSERT"),
    "과자점": ("CAFE", "DESSERT"),
    "기타 휴게음식점": ("CAFE", "COFFEE"),
    "일반조리판매": ("FOOD", "NOODLE"),
    "푸드트럭": ("FOOD", "NOODLE"),
    "패스트푸드": ("FOOD", "FASTFOOD"),
}

# 여행지 목록에 낼 수 없는 업태 — 술집은 식사 슬롯이 될 수 없고, 편의점·부속매점은 목적지가 아니다.
# 보신탕(탕류)은 노출 자체가 부적절하다고 보고 뺀다.
EXCLUDED_UPTAE = {
    "호프/통닭", "정종/대포집/소주방", "감성주점", "라이브카페", "단란주점", "유흥주점",
    "탕류(보신용)",
    "편의점", "고속도로", "백화점", "극장", "철도역구내", "유원지", "관광호텔", "키즈카페",
}

CATEGORIES = {
    # 숙박
    "문화_숙박업.csv": ("STAY", "LODGING"),
    "문화_농어촌민박업.csv": ("STAY", "RURAL_HOMESTAY"),
    "문화_한옥체험업.csv": ("STAY", "HANOK"),
    "문화_관광숙박업.csv": ("STAY", "TOURIST_HOTEL"),
    "문화_관광펜션업.csv": ("STAY", "TOURIST_PENSION"),
    "문화_외국인관광도시민박업.csv": ("STAY", "CITY_HOMESTAY"),
    # 음식 — 업태로 다시 가른다(FOOD_BY_UPTAE·CAFE_BY_UPTAE)
    "식품_일반음식점.csv": ("FOOD", "RESTAURANT"),
    "식품_휴게음식점.csv": ("CAFE", "COFFEE"),
    "식품_제과점영업.csv": ("CAFE", "BAKERY"),
    "식품_관광식당.csv": ("FOOD", "TOURIST_RESTAURANT"),
    # 볼거리
    "문화_전통사찰.csv": ("SIGHT", "TEMPLE"),
    "문화_박물관 및 미술관.csv": ("SIGHT", "MUSEUM"),
    "문화_전문휴양업.csv": ("SIGHT", "RESORT"),
    "문화_종합휴양업.csv": ("SIGHT", "RESORT"),
    "문화_일반테마파크업.csv": ("SIGHT", "THEME_PARK"),
    "문화_종합테마파크업.csv": ("SIGHT", "THEME_PARK"),
    "문화_일반야영장업.csv": ("SIGHT", "CAMPGROUND"),
    "문화_자동차야영장업.csv": ("SIGHT", "CAMPGROUND"),
    "문화_공연장.csv": ("SIGHT", "THEATER"),
    "문화_지방문화원.csv": ("SIGHT", "CULTURE_CENTER"),
    "문화_관광궤도업.csv": ("SIGHT", "CABLE_CAR"),
    "생활_골프장.csv": ("SIGHT", "GOLF"),
    "생활_스키장.csv": ("SIGHT", "SKI"),
}

# 대한민국 육지·부속도서 범위. 좌표 변환이 어긋난 행을 걸러낸다.
LAT_RANGE = (33.0, 39.0)
LNG_RANGE = (124.0, 132.0)

SEED_SQL = "src/main/resources/db/migration/V20260718200440__create_region_and_seed.sql"


def zip_entry_names(path: pathlib.Path) -> list[str]:
    """중앙 디렉터리에서 파일명 원본 바이트를 직접 읽는다.

    zipfile 은 UTF-8 플래그를 믿고 디코딩하는데, LOCALDATA ZIP 은 플래그가 켜진 채
    실제로는 cp949 라 이름이 통째로 깨진다. 여기서 플래그와 무관하게 두 인코딩을 시도한다.
    """
    raw = path.read_bytes()
    eocd = raw.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise ValueError(f"ZIP 중앙 디렉터리를 찾지 못했습니다: {path}")
    offset = struct.unpack("<I", raw[eocd + 16 : eocd + 20])[0]
    count = struct.unpack("<H", raw[eocd + 10 : eocd + 12])[0]

    names, pos = [], offset
    for _ in range(count):
        flag = struct.unpack("<H", raw[pos + 8 : pos + 10])[0]
        name_len, extra_len, comment_len = struct.unpack("<HHH", raw[pos + 28 : pos + 34])
        blob = raw[pos + 46 : pos + 46 + name_len]
        try:
            names.append(blob.decode("utf-8" if flag & 0x800 else "cp949"))
        except UnicodeDecodeError:
            names.append(blob.decode("cp949", "replace"))
        pos += 46 + name_len + extra_len + comment_len
    return names


def load_regions(repo_root: pathlib.Path) -> dict[str, list[tuple[str, int]]]:
    """89곳을 시군구 이름으로 색인한다. seed INSERT 순서가 곧 region_id 다."""
    sql = (repo_root / SEED_SQL).read_text(encoding="utf-8")
    pairs = re.findall(r"\('([^']+)','([^']+)','[\d-]+','http", sql)
    if not pairs:
        raise ValueError("region seed 를 파싱하지 못했습니다")

    by_sigungu: dict[str, list[tuple[str, int]]] = collections.defaultdict(list)
    for region_id, (sido, sigungu) in enumerate(pairs, start=1):
        by_sigungu[sigungu].append((sido, region_id))
    return by_sigungu


def resolve_region(address: str, by_sigungu) -> int | None:
    """주소에서 region_id 를 찾는다.

    동명 시군구(강원/경남 고성군 등)가 있으므로 시도명 앞 두 글자로 가른다 — 데이터마다
    '강원특별자치도'/'강원도' 처럼 표기가 달라 전체 문자열 비교는 어긋난다.
    """
    parts = address.split()
    if len(parts) < 2:
        return None
    candidates = by_sigungu.get(parts[1])
    if not candidates:
        return None
    for sido, region_id in candidates:
        if sido[:2] == parts[0][:2]:
            return region_id
    return candidates[0][1] if len(candidates) == 1 else None


def resolve_category(file_name: str, default_mapping: tuple, record: dict) -> tuple:
    """업종 파일과 `업태구분명` 을 함께 보고 (종류, 분류)를 정한다.

    숙박·볼거리는 파일 하나가 곧 한 분류라 기본값을 그대로 쓴다. 음식점·휴게음식점만 파일 안에
    온갖 업태가 섞여 있어 다시 가른다 — 갈라 놓지 않으면 코스 식사 자리에 호프집이 들어간다.

    @return (종류, 분류). 여행지로 낼 수 없는 업태면 (None, None)
    """
    if file_name not in ("식품_일반음식점.csv", "식품_휴게음식점.csv"):
        return default_mapping

    uptae = (record.get("업태구분명") or record.get("위생업태명") or "").strip()
    if uptae in EXCLUDED_UPTAE:
        return (None, None)

    table = FOOD_BY_UPTAE if file_name == "식품_일반음식점.csv" else CAFE_BY_UPTAE
    # 표에 없는 업태는 기본값으로 둔다. 원본에 새 업태가 생겨도 조용히 사라지지 않게 하기 위해서다
    # (빠뜨림은 통계에 안 잡히지만, 기본 분류로 실리면 목록에서 눈에 띈다).
    return table.get(uptae, default_mapping)


def build(zips_dir: pathlib.Path, out_path: pathlib.Path, repo_root: pathlib.Path) -> None:
    try:
        from pyproj import Transformer
    except ImportError:
        sys.exit("pyproj 가 필요합니다: python3 -m pip install pyproj")

    by_sigungu = load_regions(repo_root)
    # LOCALDATA 좌표는 EPSG:5174(Korean 1985 / Modified Central Belt).
    # 의성군청 좌표와 대조해 확정했다 — 5186 으로 읽으면 1도 가까이 어긋난다.
    transformer = Transformer.from_crs("EPSG:5174", "EPSG:4326", always_xy=True)

    zip_files = sorted(zips_dir.glob("*.zip"))
    if not zip_files:
        sys.exit(f"ZIP 이 없습니다: {zips_dir}")

    rows, stats = [], collections.Counter()
    for zip_path in zip_files:
        names = zip_entry_names(zip_path)
        archive = zipfile.ZipFile(zip_path)
        # strict=True — 중앙 디렉터리 파싱 결과와 zipfile 의 엔트리 수가 어긋나면 이름이 밀린 채
        # 엉뚱한 파일을 읽게 된다. 조용히 짧은 쪽에서 잘리지 않도록 그 자리에서 실패시킨다.
        for name, info in zip(names, archive.infolist(), strict=True):
            mapping = CATEGORIES.get(name)
            if mapping is None:
                continue
            text = archive.read(info).decode("cp949", "replace")
            for record in csv.DictReader(text.splitlines()):
                # 폐업·휴업을 걸러낸다. 경북 숙박은 41% 가 폐업이라, 두면 없어진 업소가 코스에 들어간다.
                if not (record.get("영업상태명") or "").startswith("영업"):
                    stats["폐업·휴업"] += 1
                    continue
                kind, category = resolve_category(name, mapping, record)
                if kind is None:
                    stats["업태제외"] += 1
                    continue
                address = (record.get("도로명주소") or record.get("지번주소") or "").strip()
                region_id = resolve_region(address, by_sigungu)
                if region_id is None:
                    continue  # 89곳 밖 — 대부분이 여기로 빠진다
                try:
                    x, y = float(record["좌표정보(X)"]), float(record["좌표정보(Y)"])
                except (KeyError, TypeError, ValueError):
                    stats["좌표없음"] += 1
                    continue
                lng, lat = transformer.transform(x, y)
                if not (LAT_RANGE[0] < lat < LAT_RANGE[1] and LNG_RANGE[0] < lng < LNG_RANGE[1]):
                    stats["좌표이상"] += 1
                    continue
                name_value = (record.get("사업장명") or "").strip()
                if not name_value:
                    stats["상호없음"] += 1
                    continue
                rows.append(
                    (region_id, kind, category, name_value, address,
                     (record.get("전화번호") or "").strip(), f"{lat:.7f}", f"{lng:.7f}")
                )
                stats[kind] += 1
        archive.close()

    # 같은 자리의 중복을 접는다. 원본에는 한 건물의 여러 업소가 같은 상호로 올라온다
    # (이마트 제천점 9건, 휴게소 3건 등). 코스에서는 한 곳이므로 하나만 남긴다.
    # 분류가 갈리면 관광 콘텐츠성이 높은 쪽을 남긴다.
    best: dict[tuple, tuple] = {}
    for row in rows:
        key = (row[0], row[1], row[3], row[4])  # region_id · kind · name · address
        current = best.get(key)
        if current is None or CATEGORY_PRIORITY.get(row[2], 9) < CATEGORY_PRIORITY.get(current[2], 9):
            best[key] = row
    folded = len(rows) - len(best)
    rows = list(best.values())

    rows.sort(key=lambda r: (r[0], r[1], r[3]))

    out_path.parent.mkdir(parents=True, exist_ok=True)
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(["region_id", "kind", "category", "name", "address", "tel", "lat", "lng"])
    writer.writerows(rows)
    # mtime 을 0 으로 고정해 같은 입력이면 같은 바이트가 나오게 한다(불필요한 diff 방지).
    # GzipFile.close() 는 외부에서 받은 fileobj 를 닫지 않는다. 바로 아래에서 파일 크기를 읽으므로
    # 핸들을 함께 with 로 감싸 flush·close 를 보장한다.
    with out_path.open("wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
        gz.write(buffer.getvalue().encode("utf-8"))

    per_region = collections.Counter(r[0] for r in rows)
    missing = [rid for ids in by_sigungu.values() for _, rid in ids if per_region[rid] == 0]
    size_mb = out_path.stat().st_size / 1024 / 1024
    print(f"장소 {len(rows):,}건 → {out_path} ({size_mb:.1f} MB)")
    print("  종류별: " + " · ".join(f"{k} {stats[k]:,}" for k in ("STAY", "FOOD", "CAFE", "SIGHT")))
    print("  제외:   " + " · ".join(f"{k} {stats[k]:,}" for k in ("폐업·휴업", "업태제외", "좌표없음", "좌표이상", "상호없음") if stats[k]))
    print(f"  중복 접기: {folded:,}건")
    print(f"  지역 커버: {len(per_region)}/89" + (f" — 빈 지역 {missing}" if missing else ""))


def main() -> None:
    parser = argparse.ArgumentParser(description="LOCALDATA ZIP → 장소 풀 CSV(gzip)")
    parser.add_argument("--zips", required=True, type=pathlib.Path, help="시도별 ZIP 이 있는 디렉터리")
    parser.add_argument("--out", required=True, type=pathlib.Path, help="출력 .csv.gz 경로")
    args = parser.parse_args()
    build(args.zips, args.out, pathlib.Path(__file__).resolve().parent.parent)


if __name__ == "__main__":
    main()
