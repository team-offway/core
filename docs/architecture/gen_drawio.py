"""OffWay 아키텍처 다이어그램(.drawio) 생성기.

두 페이지를 한 파일에 담는다.
  p1  시스템 조감도 — Client → Cloudflare → EC2(Docker) → MySQL/S3 + 공공 API
  p2  코스 생성 end-to-end — 사용자 입력부터 화면 렌더까지

AWS 아이콘은 draw.io 내장 aws4 스텐실(shape=mxgraph.aws4.resourceIcon)을 쓴다.
실제 셰이프 이름·카테고리 컬러는 jgraph/drawio 의 Sidebar-AWS4.js 에서 확인한 값이다.
브랜드 로고는 icons.py 가 구운 percent-encoded SVG 타일을 박는다.
"""

import json
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).parent
ICONS = json.loads((HERE / "icons.json").read_text(encoding="utf-8"))

# ── 스타일 ────────────────────────────────────────────────────────────────────
# AWS4 resourceIcon 의 연결점 목록. 사이드바가 쓰는 것과 같은 값이라 화살표가 타일
# 가장자리에 정확히 붙는다.
AWS_PTS = ("points=[[0,0,0],[0.25,0,0],[0.5,0,0],[0.75,0,0],[1,0,0],[0,1,0],[0.25,1,0],"
           "[0.5,1,0],[0.75,1,0],[1,1,0],[0,0.25,0],[0,0.5,0],[0,0.75,0],[1,0.25,0],"
           "[1,0.5,0],[1,0.75,0]];")

AWS_COMPUTE = "#ED7100"   # Compute · Containers
AWS_STORAGE = "#7AA116"   # Storage
AWS_INK = "#232F3E"
GREY_INK = "#5A6C86"

TILE_LABEL = ("dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;"
              "fontSize=11;fontStyle=0;fontColor=" + AWS_INK + ";")


def aws_res(res: str, color: str) -> str:
    return (f"sketch=0;{AWS_PTS}outlineConnect=0;fillColor={color};strokeColor=#ffffff;"
            f"aspect=fixed;{TILE_LABEL}"
            f"shape=mxgraph.aws4.resourceIcon;resIcon=mxgraph.aws4.{res};")


def img_tile(slug: str) -> str:
    return ("sketch=0;outlineConnect=0;aspect=fixed;imageAspect=0;"
            f"{TILE_LABEL}shape=image;image={ICONS[slug]};")


def aws_group(gr_icon: str, stroke: str, font: str, fill: str = "none",
              width: int = 1, size: int = 13) -> str:
    return ("sketch=0;outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;"
            f"fontSize={size};fontStyle=1;shape=mxgraph.aws4.group;strokeWidth={width};"
            f"grIcon=mxgraph.aws4.{gr_icon};strokeColor={stroke};fillColor={fill};"
            f"verticalAlign=top;align=left;spacingLeft=34;fontColor={font};dashed=0;")


LANE = ("rounded=1;arcSize=4;whiteSpace=wrap;html=1;fillColor=#F8F8F8;strokeColor=#BFBFBF;"
        "dashed=1;dashPattern=6 6;verticalAlign=top;align=center;fontSize=14;fontStyle=2;"
        "fontColor=#8A8A8A;spacingTop=6;")

BOX = ("rounded=1;arcSize=8;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#C9D2DC;"
       "align=left;verticalAlign=middle;fontSize=11;fontColor=#1F2933;spacingLeft=10;"
       "spacingRight=8;")

BOX_ERR = ("rounded=1;arcSize=8;whiteSpace=wrap;html=1;fillColor=#FDECEE;strokeColor=#DD344C;"
           "align=left;verticalAlign=middle;fontSize=11;fontColor=#8C1D2B;spacingLeft=10;"
           "spacingRight=8;")


def box_with_icon(slug: str, fill: str = "#FFFFFF", stroke: str = "#C9D2DC") -> str:
    return ("shape=label;rounded=1;arcSize=8;whiteSpace=wrap;html=1;"
            f"fillColor={fill};strokeColor={stroke};align=left;verticalAlign=middle;fontSize=11;"
            "fontColor=#1F2933;spacingLeft=42;spacingRight=8;imageWidth=24;imageHeight=24;"
            f"imageAlign=left;imageVerticalAlign=middle;spacing=8;image={ICONS[slug]};")


def note(fill="#FFF8E6", stroke="#E3C97F", ink="#6B5A22") -> str:
    return ("rounded=1;arcSize=10;whiteSpace=wrap;html=1;dashed=0;"
            f"fillColor={fill};strokeColor={stroke};align=left;verticalAlign=middle;fontSize=10;"
            f"fontColor={ink};spacingLeft=8;spacingRight=6;")


EDGE = ("edgeStyle=orthogonalEdgeStyle;rounded=1;arcSize=8;html=1;jettySize=auto;"
        f"strokeColor={GREY_INK};strokeWidth=1.5;endArrow=blockThin;endFill=1;fontSize=10;"
        "fontColor=#48607A;labelBackgroundColor=#FFFFFF;")
EDGE_SOFT = EDGE.replace(f"strokeColor={GREY_INK}", "strokeColor=#A8B4C2")
EDGE_BACK = EDGE_SOFT + "dashed=1;dashPattern=6 4;"

TITLE = "text;html=1;align=left;verticalAlign=middle;fontSize=22;fontStyle=1;fontColor=#1F2933;"
SUBTITLE = "text;html=1;align=left;verticalAlign=middle;fontSize=12;fontColor=#7A8794;"


def lbl(*lines: str) -> str:
    """라벨은 이스케이프가 2단이다. 여기서는 HTML 단계 — html=1 이라 개행은 <br> 이고,
    본문의 <>& 는 HTML 엔티티로 바꾼다. XML 단계(속성값 이스케이프)는 attr() 이 맡는다.
    한 단계라도 빠뜨리면 파일이 깨지거나(속성 안의 raw <) 태그가 글자로 보인다."""
    return "<br>".join(escape(x) for x in lines)


def attr(value: str) -> str:
    """XML 속성값 단계. lbl() 이 만든 HTML 문자열의 < > & " 를 전부 엔티티로 바꾼다."""
    return escape(value, {'"': "&quot;"})


# ── XML 조립 ─────────────────────────────────────────────────────────────────
class Page:
    def __init__(self, name: str, width: int, height: int):
        self.name, self.width, self.height = name, width, height
        self.cells: list[str] = []
        self._n = 0

    def _id(self) -> str:
        self._n += 1
        return f"c{self._n}"

    def node(self, label, x, y, w, h, style, parent="1"):
        cid = self._id()
        self.cells.append(
            f'        <mxCell id="{cid}" value="{attr(label)}" style="{attr(style)}" '
            f'vertex="1" parent="{parent}">\n'
            f'          <mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/>\n'
            f'        </mxCell>')
        return cid

    def edge(self, src, dst, label="", style=EDGE, exit_=None, entry=None, points=None):
        cid = self._id()
        s = style
        if exit_:
            s += f"exitX={exit_[0]};exitY={exit_[1]};exitDx=0;exitDy=0;"
        if entry:
            s += f"entryX={entry[0]};entryY={entry[1]};entryDx=0;entryDy=0;"
        pts = ""
        if points:
            inner = "".join(f'<mxPoint x="{px}" y="{py}"/>' for px, py in points)
            pts = f'\n            <Array as="points">{inner}</Array>\n          '
        self.cells.append(
            f'        <mxCell id="{cid}" value="{attr(label)}" style="{attr(s)}" '
            f'edge="1" parent="1" source="{src}" target="{dst}">\n'
            f'          <mxGeometry relative="1" as="geometry">{pts}</mxGeometry>\n'
            f'        </mxCell>')
        return cid

    def edge_free(self, x1, y1, x2, y2, style):
        """좌표로 직접 긋는 엣지. 시퀀스의 가로 화살표는 붙일 노드가 없다(생명선은 선 하나다)."""
        cid = self._id()
        self.cells.append(
            f'        <mxCell id="{cid}" value="" style="{attr(style)}" edge="1" parent="1">\n'
            f'          <mxGeometry relative="1" as="geometry">\n'
            f'            <mxPoint x="{x1}" y="{y1}" as="sourcePoint"/>\n'
            f'            <mxPoint x="{x2}" y="{y2}" as="targetPoint"/>\n'
            f'          </mxGeometry>\n'
            f'        </mxCell>')
        return cid

    def render(self, idx: int) -> str:
        body = "\n".join(self.cells)
        return (f'  <diagram id="page-{idx}" name="{escape(self.name)}">\n'
                f'    <mxGraphModel dx="1400" dy="800" grid="0" gridSize="10" guides="1" '
                f'tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" '
                f'pageWidth="{self.width}" pageHeight="{self.height}" math="0" shadow="0">\n'
                f'      <root>\n        <mxCell id="0"/>\n        <mxCell id="1" parent="0"/>\n'
                f'{body}\n      </root>\n    </mxGraphModel>\n  </diagram>')


# ── 페이지 1 · 시스템 조감도 ──────────────────────────────────────────────────
def page_overview() -> Page:
    """라벨은 한 줄로 짧게 둔다. 인스턴스명·컨테이너명 같은 고유 이름은 쓰지 않는다 —
    조감도에서 알고 싶은 것은 '무엇이 무엇에 붙어 있나' 지 '그것의 이름' 이 아니다.
    화살표는 코리더를 서로 다르게 잡는다. 같은 선을 겹쳐 놓으면 양방향처럼 읽힌다."""
    p = Page("1. 시스템 조감도", 1750, 1100)


    # ── 레인
    p.node(lbl("Client"), 40, 110, 200, 520, LANE)
    p.node(lbl("DNS / Edge"), 265, 110, 170, 520, LANE)
    push_lane = p.node(lbl("알림 · 푸시"), 1490, 110, 180, 470, LANE)
    p.node(lbl("외부 API (공공 데이터 · 소셜)"), 460, 790, 1000, 190,
           LANE.replace("align=center", "align=left") + "spacingLeft=16;")

    # ── Client
    users = p.node(lbl("사용자"), 106, 164, 68, 68,
                   "sketch=0;outlineConnect=0;gradientColor=none;strokeColor=none;"
                   "fillColor=#879196;aspect=fixed;dashed=0;verticalLabelPosition=bottom;"
                   "verticalAlign=top;align=center;html=1;fontSize=11;fontColor=#545B64;"
                   "shape=mxgraph.aws4.illustration_users;pointerEvents=1;")
    app = p.node(lbl("OffWay iOS 앱"), 107, 313, 66, 66, img_tile("flutter"))
    navermap = p.node(lbl("네이버 지도 SDK"), 107, 473, 66, 66, img_tile("naver"))

    # ── Edge
    cf = p.node(lbl("Cloudflare"), 317, 193, 66, 66, img_tile("cloudflare"))

    # ── AWS
    p.node(lbl("AWS Cloud · ap-northeast-1"), 460, 100, 1000, 620,
           aws_group("group_aws_cloud_alt", AWS_INK, AWS_INK, "#F2F5F8", width=3, size=15))
    p.node(lbl("Security Group"), 490, 165, 610, 525,
           aws_group("group_security_group", "#7AA116", "#248814", "#EDF4DF", width=2))
    p.node(lbl("EC2"), 520, 225, 550, 440,
           aws_group("group_ec2_instance_contents", AWS_COMPUTE, AWS_COMPUTE, "#FDF3E9", width=2))
    dock = p.node(lbl("Docker"), 550, 290, 490, 350,
                  "shape=label;rounded=1;arcSize=4;whiteSpace=wrap;html=1;fillColor=#EAF3FE;"
                  "strokeColor=#1D63ED;dashed=0;verticalAlign=top;align=left;spacingLeft=38;"
                  "spacingTop=4;fontSize=12;fontColor=#12459E;imageWidth=22;imageHeight=22;"
                  f"imageAlign=left;imageVerticalAlign=top;spacing=8;image={ICONS['docker']};")

    caddy = p.node(lbl("Caddy"), 605, 345, 54, 54, img_tile("caddy"))
    core = p.node(lbl("Spring Boot"), 815, 345, 54, 54, img_tile("springboot"))
    mysql = p.node(lbl("MySQL 8.4"), 955, 535, 54, 54, img_tile("mysql"))
    cache = p.node(lbl("ExternalDataCache"), 640, 535, 160, 40,
                   note() + "align=center;fontSize=11;fontStyle=1;")

    ecr = p.node(lbl("ECR"), 1166, 203, 66, 66, aws_res("ecr", AWS_COMPUTE))
    s3 = p.node(lbl("S3 · 썸네일"), 1166, 473, 66, 66, aws_res("s3", AWS_STORAGE))


    # ── 외부 API (가로 스트립)
    ext = [("glyph_globe", "TourAPI"), ("glyph_route", "TMAP · TAGO"),
           ("glyph_cloud", "기상청"), ("glyph_calendar", "특일정보"), ("kakao", "Kakao")]
    ext_nodes = [p.node(lbl(text), 525 + i * 195, 833, 54, 54, img_tile(slug))
                 for i, (slug, text) in enumerate(ext)]

    # ── 알림
    p.node(lbl("APNs"), 1553, 175, 54, 54, img_tile("apple"))
    p.node(lbl("FCM"), 1553, 300, 54, 54, img_tile("firebase"))
    p.node(lbl("Discord"), 1553, 425, 54, 54, img_tile("discord"))

    # ── 연결 (요청 경로)
    p.edge(users, app, "", exit_=(0.5, 1), entry=(0.5, 0))
    p.edge(app, navermap, lbl("지도 렌더"), EDGE_SOFT, exit_=(0.3, 1), entry=(0.3, 0))
    p.edge(app, cf, lbl("요청"), exit_=(1, 0.25), entry=(0, 0.35), points=[(256, 330)])
    p.edge(cf, caddy, lbl("443"), exit_=(1, 0.5), entry=(0, 0.5))
    p.edge(caddy, core, lbl(":8080"), exit_=(1, 0.5), entry=(0, 0.5))
    # core 에서 나가는 네 갈래. 나가는 면과 높이를 전부 달리해 라벨이 겹치지 않게 한다.
    p.edge(core, cache, "", exit_=(0.2, 1), entry=(0.5, 0))
    p.edge(core, mysql, lbl("JDBC"), exit_=(0.8, 1), entry=(0.5, 0))
    p.edge(core, s3, "", exit_=(1, 0.5), entry=(0, 0.5), points=[(1120, 372), (1120, 506)])

    # 이미지는 ECR 에서 받아 온다. 배포 절차 자체는 3페이지가 말한다.
    p.edge(ecr, dock, lbl("pull"), exit_=(0, 0.5), entry=(1, 0.15),
           points=[(1110, 236), (1110, 342)])

    # 외부 호출은 캐시에서 아래로 부채꼴. 출발 x 를 달리해 세로선이 겹치지 않게 한다.
    for i, n in enumerate(ext_nodes):
        p.edge(cache, n, "", EDGE_SOFT, exit_=(0.1 + i * 0.2, 1), entry=(0.5, 0))

    # 푸시는 레인 하나로 묶는다 — 세 줄을 나란히 그으면 버스처럼 뭉쳐 읽힌다.
    p.edge(core, push_lane, lbl("푸시"), exit_=(1, 0.8), entry=(0, 0.5),
           points=[(1330, 388), (1330, 345)])

    # 응답은 요청과 다른 세로 코리더(x=225)로 짧게 돌려보낸다. 같은 길을 되짚으면
    # 화살표 둘이 겹쳐 양방향 하나로 보인다.
    p.edge(cf, app, lbl("응답"), EDGE_BACK, exit_=(0.5, 1), entry=(0.75, 1),
           points=[(350, 432), (160, 432)])
    return p


# ── 페이지 2 · 코스 만들기 여정 (UX) ─────────────────────────────────────────
# 클래스명·이슈번호는 쓰지 않는다. 이 장의 질문은 "사용자가 무엇을 하면 무엇이 돌아오나" 이지
# "어느 구현체가 무엇을 했나" 가 아니다.

STEP_HEAD = ("rounded=1;arcSize=14;whiteSpace=wrap;html=1;fillColor=#1F2933;strokeColor=none;"
             "align=center;verticalAlign=middle;fontSize=12;fontStyle=1;fontColor=#FFFFFF;")
ROW_HEAD = ("rounded=1;arcSize=10;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#CBD5E0;"
            "align=left;verticalAlign=middle;fontSize=12;fontStyle=1;fontColor=#1F2933;"
            "spacingLeft=12;")


def band(fill: str, stroke: str) -> str:
    return ("rounded=1;arcSize=3;whiteSpace=wrap;html=1;dashed=0;"
            f"fillColor={fill};strokeColor={stroke};align=left;verticalAlign=top;fontSize=1;"
            "fontColor=none;")


def cell(fill: str, stroke: str, ink: str) -> str:
    return ("rounded=1;arcSize=10;whiteSpace=wrap;html=1;dashed=0;"
            f"fillColor={fill};strokeColor={stroke};align=left;verticalAlign=top;fontSize=11;"
            f"fontColor={ink};spacingLeft=10;spacingRight=8;spacingTop=6;")


def cell_icon(slug: str, fill: str, stroke: str, ink: str) -> str:
    return ("shape=label;rounded=1;arcSize=10;whiteSpace=wrap;html=1;dashed=0;"
            f"fillColor={fill};strokeColor={stroke};align=left;verticalAlign=top;fontSize=11;"
            f"fontColor={ink};spacingLeft=38;spacingRight=8;spacingTop=6;imageWidth=22;"
            f"imageHeight=22;imageAlign=left;imageVerticalAlign=top;spacing=8;"
            f"image={ICONS[slug]};")


def page_course_flow() -> Page:
    """세로 타임라인. 축은 둘뿐이다 — 사용자가 겪는 것과, 그 뒤에서 서버가 한 것.

    가로 8열 표였던 것을 세로로 세운 이유는 README 가 세로로 긴 매체라서다. 폭 900px 에
    가로 2000px 을 욱여넣으면 글자가 4px 이 된다 — 3배로 뽑아도 '선명한 4px' 일 뿐이다.
    """
    RAIL_X, BADGE_X, CX, CW = 44, 30, 82, 800
    TXT_X, LBL_W = 158, 56

    BADGE = ("ellipse;whiteSpace=wrap;html=1;fillColor=#1F2933;strokeColor=#FFFFFF;"
             "strokeWidth=2;align=center;verticalAlign=middle;fontSize=11;fontStyle=1;"
             "fontColor=#FFFFFF;")
    RAIL = "shape=line;direction=north;html=1;strokeColor=#D7DEE6;strokeWidth=2;"
    H_TITLE = ("text;html=1;align=left;verticalAlign=middle;fontSize=14;fontStyle=1;"
               "fontColor=#1F2933;")
    H_SCREEN = ("text;html=1;align=right;verticalAlign=middle;fontSize=11;fontStyle=2;"
                "fontColor=#8A97A6;")
    ROW_USER = ("rounded=1;arcSize=8;whiteSpace=wrap;html=1;fillColor=#FFFFFF;"
                "strokeColor=#DDE3EA;align=left;verticalAlign=top;fontSize=12;"
                "fontColor=#1F2933;spacingLeft=10;spacingRight=10;spacingTop=2;")
    ROW_SRV = ROW_USER.replace("#FFFFFF", "#F2F7FF").replace("#DDE3EA", "#C9DCF5")                       .replace("fontColor=#1F2933", "fontColor=#24405F")
    ROW_LBL = ("text;html=1;align=left;verticalAlign=top;fontSize=11;fontStyle=1;"
               "fontColor=#8A97A6;")
    ROW_LBL_S = ROW_LBL.replace("#8A97A6", "#5B7FA8")
    SEEN = ("text;html=1;align=left;verticalAlign=top;fontSize=11;fontColor=#7A8794;")
    CHIP = ("rounded=1;arcSize=40;whiteSpace=wrap;html=1;fillColor=#E8F0FC;"
            "strokeColor=#B7CFEE;align=center;verticalAlign=middle;fontSize=10;"
            "fontColor=#3A5C85;")
    CHIP_NONE = CHIP.replace("#E8F0FC", "#EFF6EC").replace("#B7CFEE", "#BBD8AE")                     .replace("#3A5C85", "#41663A")

    # (번호, 단계, 화면, 사용자가 하는 일, 화면에 돌아오는 것, 서버가 하는 일, 칩, 칩색)
    steps = [
        ("1", "시작", "로그인 · 남은 연차",
         "카카오 또는 Apple 로그인, 올해 남은 연차 입력", "홈 진입",
         "소셜 계정 확인 후 토큰 발급, 남은 연차 저장", "Kakao · Apple", False),
        ("2", "홈", "추천 카드",
         "추천 훑어보기, '코스 만들기' 누르기", "다가오는 황금연휴 · 추천 명소 카드",
         "공휴일과 남은 연차로 갈 수 있는 날 계산, 추천할 곳 미리 예열",
         "특일정보 (캐시)", False),
        ("3", "출발지", "출발지 검색",
         "사는 곳이나 출발할 역 · 터미널 검색 후 선택", "검색 결과 목록 (역 · 터미널 · 지역)",
         "검색어로 장소 조회, 출발 시 닿는 교통 거점 산출 (위치 권한 없음)",
         "Kakao 장소검색", False),
        ("4", "날짜", "달력",
         "떠날 날과 돌아올 날 선택", "달력의 공휴일 표시, '연차 O일로 O박 O일'",
         "공휴일 · 주말 반영, 소모 연차와 실제 가용 시간 산출", "특일정보 (캐시)", False),
        ("5", "기간 · 이동수단 · 밀도", "선택 3단계",
         "며칠, 무엇을 타고, 얼마나 빽빽하게", "대기 없이 다음 화면",
         "세 선택은 앱 안에 누적해 두었다가 다음 단계에서 한 번에 보낸다",
         "호출 없음", True),
        ("6", "후보 지역", "지역 카드",
         "갈 수 있는 지역 중 하나 선택 (고르기 어려우면 랜덤)", "지역 카드 (사진 · 이동시간 · 태그)",
         "출발지에서 그 기간 안에 닿는 인구감소지역만 추려 순서대로 응답",
         "사전 적재 DB", True),
        ("7", "코스", "지도 + 일정표",
         "날짜별 일정 확인, 마음에 안 드는 장소 교체 요청",
         "지도 위 동선 · 날짜별 일정표 · 날씨 · 혜택 배지",
         "볼거리 · 끼니 · 숙소 · 카페 선별, 인접한 것끼리 묶어 하루 단위 시간표 배치, "
         "날씨와 혜택 첨부", "TMAP · 기상청", False),
        ("8", "저장 · 공유", "내 코스 · 잠금화면",
         "코스 저장과 링크 공유, 여행 당일 잠금화면 확인",
         "내 코스 목록 · 공유 링크 · 잠금화면 실시간 카드",
         "코스 저장과 공유 링크 생성, 여행 전날 · 당일 알림과 잠금화면 카드 발송",
         "APNs", False),
    ]

    def lines(text: str, width_px: int, per_char: float = 11.2) -> int:
        """한글 기준 대략적인 줄 수. 상자 높이를 내용에 맞추려고 쓴다 —
        칸을 전부 같은 높이로 두면 어떤 칸은 텅 비고 어떤 칸은 꽉 차서 격자가 거슬린다."""
        cap = max(1, int(width_px / per_char))
        return max(1, -(-len(text) // cap))

    p = Page("2. 코스 만들기 사용자 여정", 920, 40)

    y = 30
    badges: list[tuple[str, int]] = []
    for num, title, screen, act, seen, srv, chip, chip_green in steps:
        badges.append((num, y + 2))
        p.node(lbl(title), CX, y, 520, 32, H_TITLE)
        p.node(lbl(screen), CX + CW - 300, y, 300, 32, H_SCREEN)
        y += 36

        # 사용자 행 — 하는 일 + 그래서 화면에 무엇이 돌아오는가.
        uh = 22 + lines(act, CW - (TXT_X - CX) - 16) * 18 + lines(seen, CW - (TXT_X - CX) - 16) * 17
        p.node("", CX, y, CW, uh, ROW_USER)
        p.node(lbl("사용자"), CX + 12, y + 8, LBL_W, 18, ROW_LBL)
        p.node(lbl(act), TXT_X, y + 6, CW - (TXT_X - CX) - 16, 20, ROW_USER
               .replace("fillColor=#FFFFFF", "fillColor=none").replace("strokeColor=#DDE3EA", "strokeColor=none")
               .replace("spacingLeft=10", "spacingLeft=0"))
        p.node(lbl("→ " + seen), TXT_X, y + 6 + lines(act, CW - (TXT_X - CX) - 16) * 18, 
               CW - (TXT_X - CX) - 16, 20, SEEN)
        y += uh + 6

        # 서버 행 — 칩이 오른쪽 끝에 붙으므로 본문 폭을 그만큼 줄인다.
        chip_w = 118 if len(chip) <= 9 else 138
        sw = CW - (TXT_X - CX) - 16 - chip_w - 12
        sh = 18 + lines(srv, sw) * 18
        p.node("", CX, y, CW, sh, ROW_SRV)
        p.node(lbl("서버"), CX + 12, y + 8, LBL_W, 18, ROW_LBL_S)
        p.node(lbl(srv), TXT_X, y + 6, sw, sh - 12, ROW_SRV
               .replace("fillColor=#F2F7FF", "fillColor=none").replace("strokeColor=#C9DCF5", "strokeColor=none")
               .replace("spacingLeft=10", "spacingLeft=0"))
        p.node(lbl(chip), CX + CW - chip_w - 12, y + 8, chip_w, 20,
               CHIP_NONE if chip_green else CHIP)
        y += sh + 22

    # 레일을 먼저 긋고 배지를 그 위에 얹는다 — 순서를 바꾸면 선이 숫자를 지운다.
    top, bottom = badges[0][1] + 14, badges[-1][1] + 14
    p.node("", RAIL_X - 1, top, 2, bottom - top, RAIL)
    for num, by in badges:
        p.node(lbl(num), BADGE_X, by, 28, 28, BADGE)

    p.node(lbl("모든 요청은 Cloudflare → EC2 위 Docker 안의 서버로 간다. "
               "공공 데이터(관광 · 교통 · 날씨 · 공휴일)는 미리 받아 캐시와 DB 에 적재해 두므로, "
               "외부 API 가 느린 날에도 화면이 기다리지 않는다.",
               "⑤처럼 서버를 아예 부르지 않는 단계도 있다 — 고르기만 하는 화면은 앱 안에서 처리하고, "
               "호출은 꼭 필요한 순간에만 나간다."),
           CX, y - 8, CW, 56, note())

    p.height = y + 62
    return p


# ── 페이지 3 · 배포 시퀀스 다이어그램 ────────────────────────────────────────
# UML 시퀀스 규약을 따른다 — 생명선 · 활성 막대 · 실선(호출)/점선(반환) · combined fragment.
# 배포의 요점은 순서다: EC2 의 22번은 특정 IP 하나만 열려 있어서, 러너 IP 를 허용에 넣고
# 시작해 회수하며 끝난다. 회수는 앞이 실패해도 도는 단계라 fragment 로 묶어 표시한다.

ACTOR = ("shape=label;rounded=1;arcSize=12;whiteSpace=wrap;html=1;fillColor=#1F2933;"
         "strokeColor=none;align=center;verticalAlign=middle;fontSize=12;fontStyle=1;"
         "fontColor=#FFFFFF;spacingLeft=20;imageWidth=20;imageHeight=20;imageAlign=left;"
         "imageVerticalAlign=middle;spacing=10;image={img};")
LIFELINE = "shape=line;direction=north;html=1;strokeColor=#B6C2CE;strokeWidth=1;dashed=1;"
ACTIVATION = ("rounded=0;html=1;fillColor=#DCE6F1;strokeColor=#7C94B0;strokeWidth=1;"
              "verticalAlign=top;fontSize=1;fontColor=none;")
STEP = ("rounded=1;arcSize=8;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#D7DEE6;"
        "align=left;verticalAlign=middle;fontSize=11;fontColor=#1F2933;spacingLeft=10;")
STEP_GATE = ("rounded=1;arcSize=8;whiteSpace=wrap;html=1;fillColor=#FDECEE;strokeColor=#DD344C;"
             "align=left;verticalAlign=middle;fontSize=11;fontStyle=1;fontColor=#8C1D2B;"
             "spacingLeft=10;")
CALL = ("html=1;rounded=0;endArrow=block;endFill=1;strokeColor=#3D4C5C;strokeWidth=1.4;"
        "edgeStyle=none;")
CALL_GATE = CALL.replace("strokeColor=#3D4C5C", "strokeColor=#DD344C") + "strokeWidth=2;"
RETURN = ("html=1;rounded=0;endArrow=open;endFill=0;endSize=8;strokeColor=#8A97A6;"
          "strokeWidth=1.2;dashed=1;dashPattern=5 4;edgeStyle=none;")
MSG = "text;html=1;align=center;verticalAlign=bottom;fontSize=10;fontColor=#3D4C5C;"
MSG_GATE = "text;html=1;align=center;verticalAlign=bottom;fontSize=10;fontColor=#8C1D2B;fontStyle=1;"


def page_deploy() -> Page:
    p = Page("3. 배포 시퀀스", 1700, 860)

    actors = [("GitHub Actions", "githubactions", 560, 200),
              ("Security Group", None, 810, 190),
              ("ECR", None, 1050, 150),
              ("EC2 · Docker", "docker", 1240, 190),
              ("Discord", "discord", 1470, 150)]
    cx = {}
    for name, slug, x, w in actors:
        style = ACTOR.format(img=ICONS[slug]) if slug else ACTOR.format(img="").replace(
            "shape=label;", "")
        p.node(lbl(name), x, 112, w, 46, style)
        cx[name] = x + w // 2

    GHA, SG, ECR, EC2, DC = ("GitHub Actions", "Security Group", "ECR", "EC2 · Docker",
                             "Discord")
    # (설명, 보내는 쪽, 받는 쪽, 메시지, 반환인가, fragment 경계인가)
    steps = [
        ("① dev 브랜치 push, 체크아웃 · 커밋 검증 · jar 빌드", GHA, GHA, "build", False, False),
        ("② 러너 IP 를 SSH(22) 인바운드 규칙에 추가", GHA, SG,
         "authorize-ingress(runner IP, 22)", False, True),
        ("③ SSH 접속 후 빌드 캐시 · 옛 이미지 정리", GHA, EC2, "ssh: prune", False, False),
        ("④ 이미지 업로드 (커밋 SHA, latest 두 태그)", GHA, ECR, "docker push", False, False),
        ("⑤ 환경변수 파일 전송 (권한 600)", GHA, EC2, "scp: env.prod", False, False),
        ("⑥ 새 이미지 내려받기", EC2, ECR, "docker pull", False, False),
        ("⑦ 컨테이너 교체 (이전 이미지는 SHA 태그로 보존)", EC2, EC2, "docker run", False, False),
        ("⑧ 기동 확인 · 스모크", GHA, EC2, "healthcheck + smoke", False, False),
        ("⑨ 결과 반환, 실패 시 이전 SHA 이미지로 롤백", EC2, GHA, "result / rollback", True, False),
        ("⑩ 서비스 상태 · TLS 인증서 만료일 확인", GHA, EC2, "status + cert", False, False),
        ("⑪ 러너 IP 회수 (앞 단계 실패와 무관하게 실행, always)", GHA, SG,
         "revoke-ingress(runner IP, 22)", False, True),
        ("⑫ 배포 결과 통지 (always)", GHA, DC, "notify", False, False),
    ]

    top, pitch = 214, 60
    ys = [top + i * pitch for i in range(len(steps))]
    bottom = ys[-1] + 44

    # combined fragment: 러너 IP 가 열려 있는 구간. UML 프레임이라 모서리에 탭이 붙는다.
    p.node(lbl("critical"), 470, ys[1] - 30, 960, ys[10] - ys[1] + 60,
           "shape=umlFrame;whiteSpace=wrap;html=1;width=88;height=26;fillColor=none;"
           "strokeColor=#DD344C;dashed=1;dashPattern=6 5;align=center;verticalAlign=top;"
           "fontSize=10;fontStyle=1;fontColor=#8C1D2B;")
    p.node(lbl("[ 러너 IP 에만 SSH(22) 가 열려 있는 구간 ]"), 1130, ys[1] - 28, 290, 20,
           "text;html=1;align=right;verticalAlign=middle;fontSize=10;fontStyle=2;"
           "fontColor=#B5606C;")

    for name, _slug, x, w in actors:
        p.node("", x + w // 2, 158, 1, bottom - 158, LIFELINE)

    # 활성 막대. 러너는 처음부터 끝까지, 나머지는 메시지를 주고받는 동안만.
    p.node("", cx[GHA] - 5, ys[0] - 14, 10, ys[-1] - ys[0] + 28, ACTIVATION)
    for (_t, src, dst, _m, _r, _g), y in zip(steps, ys):
        for who in {src, dst} - {GHA}:
            p.node("", cx[who] - 5, y - 13, 10, 26, ACTIVATION)

    for (text, src, dst, msg, is_return, gate), y in zip(steps, ys):
        p.node(lbl(text), 40, y - 20, 400, 40, STEP_GATE if gate else STEP)
        if src == dst:                       # self-message: 자기 생명선으로 되돌아옴
            p.edge_free(cx[src], y - 12, cx[src] + 46, y - 12, CALL)
            p.edge_free(cx[src] + 46, y - 12, cx[src] + 46, y + 8, CALL)
            p.edge_free(cx[src] + 46, y + 8, cx[src] + 6, y + 8, CALL)
            p.node(lbl(msg), cx[src] + 52, y - 18, 130, 18,
                   "text;html=1;align=left;verticalAlign=middle;fontSize=10;fontColor=#3D4C5C;")
            continue
        style = CALL_GATE if gate else (RETURN if is_return else CALL)
        sx, dx = cx[src], cx[dst]
        p.edge_free(sx, y, dx, y, style)
        p.node(lbl(msg), min(sx, dx), y - 26, abs(dx - sx), 18, MSG_GATE if gate else MSG)

    p.node(lbl("실선 화살표는 호출, 점선 화살표는 반환. 세로 막대는 그 액터의 활성 구간.",
               "앱과 DB 의 위치는 1페이지 참고."),
           40, bottom + 24, 620, 44, note())
    return p

def main() -> None:
    pages = [page_overview(), page_course_flow(), page_deploy()]
    body = "\n".join(pg.render(i + 1) for i, pg in enumerate(pages))
    xml = ('<mxfile host="app.diagrams.net" agent="offway-arch-gen" version="24.7.17">\n'
           f'{body}\n</mxfile>\n')
    out = HERE / "offway-architecture.drawio"
    out.write_text(xml, encoding="utf-8")
    print(f"{out.name}  {out.stat().st_size / 1024:.1f} KB  pages={len(pages)}")


if __name__ == "__main__":
    main()
