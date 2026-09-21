"""Simple Icons SVG -> draw.io 에 내장할 base64 data URI 로 바꾼다.

AWS4 셰이프는 draw.io 내장이라 그대로 쓰면 되지만, Flutter·Spring·MySQL 같은 브랜드
로고는 draw.io 에 없다. Simple Icons 의 단색 glyph 를 받아 AWS4 와 같은 모양
(둥근 모서리 컬러 타일 + 흰 글리프)으로 합성해 파일에 박는다. CDN 을 참조만 하면
오프라인이나 7일 뒤에 빈 상자가 되므로 base64 로 굽는다.
"""

import json
import re
import urllib.request
from urllib.parse import quote
from pathlib import Path

CDN = "https://cdn.jsdelivr.net/npm/simple-icons@15/icons/{slug}.svg"
CACHE = Path(__file__).parent / "icon-cache"
CACHE.mkdir(exist_ok=True)

# 브랜드 컬러. Simple Icons 공식 hex 기준 (Apple 만 검정이라 타일에서 뭉개지지 않게 유지).
BRAND = {
    "cloudflare": "#F38020",
    "flutter": "#02569B",
    "springboot": "#6DB33F",
    "mysql": "#4479A1",
    "docker": "#1D63ED",
    "caddy": "#1F88C0",
    "githubactions": "#2088FF",
    "apple": "#000000",
    "firebase": "#FFCA28",
    "kakao": "#FFCD00",
    "naver": "#03C75A",
    "discord": "#5865F2",
}

# 글리프가 밝아 흰색으로 찍으면 안 보이는 것들 — AWS4 의 진한 남색을 쓴다.
DARK_GLYPH = {"firebase", "kakao"}
AWS_INK = "#232F3E"


def _fetch(slug: str) -> str:
    cached = CACHE / f"{slug}.svg"
    if not cached.exists():
        with urllib.request.urlopen(CDN.format(slug=slug), timeout=30) as r:
            cached.write_bytes(r.read())
    return cached.read_text(encoding="utf-8")


def _paths(svg: str) -> str:
    """simple-icons 는 24x24 viewBox 에 <path d="..."/> 하나다. d 만 꺼낸다."""
    found = re.findall(r'<path[^>]*\sd="([^"]+)"', svg)
    if not found:
        raise ValueError("path 를 못 찾았습니다 — simple-icons 포맷이 바뀌었는지 확인하세요")
    return found[0]


def tile(slug: str, fill: str | None = None) -> str:
    """AWS4 resourceIcon 과 같은 모양의 컬러 타일 SVG 를 data URI 로 만든다."""
    d = _paths(_fetch(slug))
    bg = fill or BRAND[slug]
    ink = AWS_INK if slug in DARK_GLYPH else "#FFFFFF"
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32">'
        f'<rect width="32" height="32" rx="3" fill="{bg}"/>'
        f'<path transform="translate(5.5 5.5) scale(0.875)" d="{d}" fill="{ink}"/>'
        "</svg>"
    )
    return _data_uri(svg)


def glyph_tile(bg: str, inner: str) -> str:
    """로고가 없는 대상(공공 API 등)을 위한 자작 글리프 타일."""
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32">'
        f'<rect width="32" height="32" rx="3" fill="{bg}"/>'
        f'<g fill="none" stroke="#FFFFFF" stroke-width="1.6" stroke-linecap="round" '
        f'stroke-linejoin="round">{inner}</g>'
        "</svg>"
    )
    return _data_uri(svg)


def _data_uri(svg: str) -> str:
    """percent-encoding 으로 굽는다. base64 를 쓰면 안 된다 — mxGraph 스타일은 `;` 로 토큰을
    나누므로 `data:image/svg+xml;base64,...` 의 세미콜론에서 값이 잘려 이미지가 통째로 사라진다
    (실제로 그렇게 빈 상자가 나왔다). `;`·`=` 까지 전부 인코딩해 토큰 하나로 만든다."""
    return "data:image/svg+xml," + quote(svg, safe="")


# 공공 API 용 글리프 — 지구본(관광·공공), 버스/길(교통), 구름(기상), 달력(공휴일)
GLYPHS = {
    "globe": '<circle cx="16" cy="16" r="9"/><path d="M7 16h18M16 7c2.6 2.6 2.6 15.4 0 18'
             'M16 7c-2.6 2.6-2.6 15.4 0 18"/>',
    "route": '<circle cx="10" cy="10" r="2.6"/><circle cx="22" cy="22" r="2.6"/>'
             '<path d="M10 12.6v4a3 3 0 0 0 3 3h6a3 3 0 0 1 3 3"/>',
    "cloud": '<path d="M11 23h11a4.5 4.5 0 0 0 .6-9 6.5 6.5 0 0 0-12.4 1.4A4.3 4.3 0 0 0 11 23z"/>',
    "calendar": '<rect x="7" y="9" width="18" height="16" rx="2"/><path d="M7 14h18M12 7v4M20 7v4"/>',
    "map": '<path d="M7 10l6-3 6 3 6-3v15l-6 3-6-3-6 3z"/><path d="M13 7v15M19 10v15"/>',
    "cache": '<ellipse cx="16" cy="10" rx="8" ry="3.2"/><path d="M8 10v12c0 1.8 3.6 3.2 8 3.2'
             's8-1.4 8-3.2V10"/><path d="M8 16c0 1.8 3.6 3.2 8 3.2s8-1.4 8-3.2"/>',
}


def dump(path: Path, mapping: dict) -> None:
    path.write_text(json.dumps(mapping, indent=2), encoding="utf-8")


if __name__ == "__main__":
    out = {slug: tile(slug) for slug in BRAND}
    for name, inner in GLYPHS.items():
        out[f"glyph_{name}"] = glyph_tile("#5A6C86", inner)
    dump(Path(__file__).parent / "icons.json", out)
    print(f"{len(out)}개 아이콘을 구웠습니다")
