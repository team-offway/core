#!/usr/bin/env python3
"""운영 로그를 읽기 좋게 물들여 내보낸다.

`docker logs` 가 주는 Spring Boot 기본 한 줄은 정보가 다 들어 있지만 눈으로 훑기 어렵다 — 타임스탬프·PID·
스레드·로거가 메시지 앞에 길게 붙어, 정작 읽고 싶은 메시지가 매번 다른 칸에서 시작한다.

여기서는 한 건을 두 줄로 편다.

    ▌INFO  00:14:59.576  ·a1b2c3·  GalleryPhotoRefreshService      [scheduling-2]
       관광사진 갤러리 적재 완료 사진=5773건 — 우리 지역에 붙은 것 1445건

- 맨 앞 기둥(▌) 색이 레벨이다. 빨강(ERROR)·노랑(WARN)만 찾으면 되므로 스크롤이 빨라진다.
- **traceId 는 값에서 색을 뽑는다.** 같은 요청이면 늘 같은 색이라, 요청 하나가 남긴 로그가 흩어져 있어도
  색으로 이어 볼 수 있다. 이게 이 스크립트의 핵심이다 — 문자열을 눈으로 대조하는 것과 차이가 크다.
- `키=값` 은 값만 밝게 둔다. 적재 건수·지역 수처럼 실제로 보고 싶은 것이 값 쪽이다.
- 스택 트레이스·매칭 안 되는 줄(기동 배너 등)은 흐리게 이어 붙여, 흐름을 끊지 않는다.

사용:
    docker logs -f offway-core 2>&1 | logfmt.py
    ... | logfmt.py --trace a1b2c3        # 그 요청만
    ... | logfmt.py --user ac120003-a034   # 그 사용자만 (앞부분만 줘도 된다)
    ... | logfmt.py --level WARN          # WARN 이상만
    ... | logfmt.py --grep 갤러리          # 메시지 정규식
    ... | logfmt.py --compact             # 한 줄로
    ... | logfmt.py --color never         # 색 없이(파일로 저장할 때. 기본은 auto)
"""

from __future__ import annotations

import argparse
import re
import sys

# **stdin·stdout 을 UTF-8 로 못 박는다.** 윈도우 파이썬은 파이프·리다이렉트에서 로케일 인코딩(cp949)을
# 쓰는데 로그는 UTF-8 이라, 그대로 읽으면 한글이 통째로 깨진다(`적재 완료` → `?쟻?옱 ?셿猷`).
# 읽는 쪽만 고치면 쓰는 쪽에서 다시 깨지므로 둘 다 잡는다. errors="replace" 는 로그 한 줄이 깨져도
# 파이프가 죽지 않게 하려는 것이다 — tail -f 를 UnicodeDecodeError 로 끊는 것이 가장 나쁘다.
for _stream in (sys.stdin, sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

# Spring Boot 기본 콘솔 한 줄. level 뒤 대괄호는 이 프로젝트의 `logging.pattern.level` 이 붙인 traceId 다.
LINE = re.compile(
    r"^(?P<ts>\d{4}-\d{2}-\d{2}T[\d:.]+(?:[+-]\d{2}:\d{2}|Z))\s+"
    r"(?P<level>[A-Z]+)\s+"
    r"\[(?P<trace>[^\]]*)\]\s+"
    r"\d+\s+---\s+"
    r"\[(?P<app>[^\]]*)\]\s+"
    r"\[(?P<thread>[^\]]*)\]\s+"
    r"(?P<logger>\S+)\s*:\s?"
    r"(?P<msg>.*)$"
)

# 메시지 안에서 눈이 가는 것들을 한 번에 훑는다. 갈래마다 따로 sub 를 돌리면 앞 갈래가 심어 둔 이스케이프
# 위에 다음 갈래가 또 얹혀 색이 깨진다 — 그래서 교대(alternation) 하나로 묶어 단일 패스로 처리한다.
#
# - kv: `사진=5773건` 처럼 키에 한글이 오는 로그가 많아 \w 로는 못 잡는다. 값에 대괄호가 오는
#   `ext=[holiday 117ms]` 는 값으로 보지 않는다(뒤 항목이 따로 물든다).
# - secs/ms: 느린 요청이 눈에 먼저 들어와야 한다. 임계는 아래 SLOW_* 가 소유한다.
HIGHLIGHT = re.compile(
    r"(?P<kv>[\w가-힣]+)=(?P<value>[^\s,\[\]]+)"
    r"|(?P<secs>\b\d+\.\d+)s\b"
    r"|(?P<ms>\b\d+)ms\b"
)

# 요청 로그는 `200 GET /path ...` 로 시작한다. 상태코드는 맨 앞이라 여기만 따로 본다.
STATUS = re.compile(r"^(\d{3})(\s+)")

# 느리다고 볼 기준(초). 코스 생성이 외부 3종을 물어 3초를 넘기는 일이 있어, 경고와 위험을 갈라 둔다.
SLOW_WARN_SECONDS = 1.0
SLOW_BAD_SECONDS = 3.0

LEVEL_ORDER = {"TRACE": 0, "DEBUG": 1, "INFO": 2, "WARN": 3, "ERROR": 4}

RESET = "\033[0m"
DIM = "\033[2m"
BOLD = "\033[1m"

LEVEL_COLOR = {
    "ERROR": "\033[91m",
    "WARN": "\033[93m",
    "INFO": "\033[92m",
    "DEBUG": "\033[94m",
    "TRACE": "\033[90m",
}

# traceId 색 후보 — 배경(검정·흰색) 어느 쪽에서도 읽히는 256색만 골랐다. 회색 계열은 DIM 과 헷갈려 뺐다.
TRACE_COLORS = [
    "\033[38;5;{}m".format(n)
    for n in (81, 209, 141, 114, 203, 179, 117, 176, 150, 215, 111, 218, 156, 180)
]

GUTTER = "▌"

# traceId 폭 — `logging.pattern.level` 의 `%-6.6X{traceId}` 와 같은 값이라야 칸이 맞는다.
TRACE_ID_WIDTH = 6

# 사용자 식별자 폭 — 같은 패턴의 `%-36.36X{userId}` (UUID 전문)와 같은 값. 앞자리만 싣던 시절에는 8이었는데,
# 우리 UUID 는 앞 8자가 컨테이너 IP 라 모든 사용자가 `ac120003` 으로 시작해 구분이 안 됐다.
USER_ID_WIDTH = 36

# 두 값이 `|` 로 이어져 한 칸을 이룬다. traceId 가 없는 줄(배치·기동)도 이 폭을 비워 둬야 칸이 안 흔들린다.
TRACE_FIELD_WIDTH = TRACE_ID_WIDTH + 1 + USER_ID_WIDTH

# 클래스명 칸 폭. 가장 긴 것이 34자(ApiResponseAuthenticationEntryPoint)라 그만큼 잡았다. 넘치는 줄은
# 그만큼 밀리지만, 대다수를 정렬해 두는 편이 이름을 자르는 것보다 낫다.
LOGGER_WIDTH = 34


class Painter:
    """색을 끄면 아무것도 안 씌우는 얇은 래퍼 — 호출부에 if 를 흩뿌리지 않으려고 둔다."""

    def __init__(self, enabled: bool):
        self.enabled = enabled

    def __call__(self, text: str, *codes: str) -> str:
        if not self.enabled or not codes:
            return text
        return "".join(codes) + text + RESET

    def trace_chip(self, trace: str) -> str:
        """`traceId|userId` 를 각각 제 색으로 물들인다.

        **두 값의 색을 따로 뽑는 것이 요점이다.** traceId 색은 요청 하나를 잇고, userId 색은 같은 사람이
        남긴 줄을 잇는다. 합쳐서 한 번에 해싱하면 요청마다 색이 바뀌어 사람 쪽 연결이 사라진다.

        폭을 고정한다 — 배치 로그처럼 이 값이 없는 줄과 칸이 어긋나면 오른쪽 로거 이름이 들쭉날쭉해져
        훑는 이점이 사라진다.
        """
        trace_id, separator, user_id = trace.partition("|")
        body = self._tint(trace_id.strip(), TRACE_ID_WIDTH)
        if separator:
            body += self("|", DIM) + self._tint(user_id.strip(), USER_ID_WIDTH)
        return self("·", DIM) + body + self("·", DIM)

    def _tint(self, value: str, width: int) -> str:
        """값에서 색을 뽑아 칠하고 폭을 맞춘다. 여백은 색 밖에 둬야 실제 칸 폭이 맞는다."""
        if not value:
            return " " * width
        color = TRACE_COLORS[sum(value.encode()) % len(TRACE_COLORS)]
        return self(value, color, BOLD) + " " * max(0, width - len(value))


def short_logger(logger: str) -> str:
    """`c.o.core.leave.service.MyLeaveService` → `MyLeaveService`.

    패키지는 버린다. 이미 축약돼 있어(`c.o.c.t.s.`) 읽어도 뜻이 잘 안 잡히는데 칸만 차지하고, 클래스명만으로
    이 프로젝트에서 헷갈리는 경우가 없다.
    """
    return logger.rpartition(".")[2]


def duration_color(seconds: float) -> str:
    if seconds >= SLOW_BAD_SECONDS:
        return LEVEL_COLOR["ERROR"]
    if seconds >= SLOW_WARN_SECONDS:
        return LEVEL_COLOR["WARN"]
    return DIM


def paint_message(message: str, paint: Painter) -> str:
    if not paint.enabled:
        return message

    status = STATUS.match(message)
    prefix = ""
    if status:
        code = status.group(1)
        family = {"2": "INFO", "3": "INFO", "4": "WARN", "5": "ERROR"}.get(code[0], "DEBUG")
        prefix = paint(code, LEVEL_COLOR[family], BOLD) + status.group(2)
        message = message[status.end() :]

    def repaint(m: re.Match) -> str:
        if m.group("kv") is not None:
            return paint(m.group("kv"), DIM) + paint("=", DIM) + paint(m.group("value"), BOLD)
        if m.group("secs") is not None:
            return paint(m.group("secs") + "s", duration_color(float(m.group("secs"))), BOLD)
        return paint(m.group("ms") + "ms", duration_color(int(m.group("ms")) / 1000), BOLD)

    return prefix + HIGHLIGHT.sub(repaint, message)


def main() -> int:
    parser = argparse.ArgumentParser(add_help=True, description="운영 로그 색칠기")
    parser.add_argument("--trace", help="이 traceId 의 로그만 본다")
    parser.add_argument("--user", help="이 사용자의 로그만 본다 (UUID 앞부분만 줘도 된다)")
    parser.add_argument("--level", default="TRACE", help="이 레벨 이상만 (기본 TRACE)")
    parser.add_argument("--grep", help="메시지 정규식으로 거른다")
    parser.add_argument("--compact", action="store_true", help="두 줄이 아니라 한 줄로")
    parser.add_argument(
        "--color",
        choices=("auto", "always", "never"),
        default="auto",
        help="색 (기본 auto — 터미널이 아니면 끈다)",
    )
    args = parser.parse_args()

    # auto 로 두면 파일·파이프로 흘릴 때 이스케이프가 섞이지 않는다. 페이저에 넘길 때만 always 를 쓴다.
    paint = Painter(enabled=args.color == "always" or (args.color == "auto" and sys.stdout.isatty()))
    floor = LEVEL_ORDER.get(args.level.upper(), 0)
    grep = re.compile(args.grep) if args.grep else None

    # 이어지는 줄(스택 트레이스)은 직전 로그의 일부다. 직전이 걸러졌으면 함께 감춘다 —
    # 그러지 않으면 필터를 걸었을 때 주인 없는 스택만 남아 더 헷갈린다.
    #
    # 첫 로그 줄보다 앞서는 것(JVM 경고·기동 배너)은 주인이 없다. 필터가 걸려 있으면 감추고, 아니면
    # 보여 준다 — 필터를 걸었는데 배너부터 쏟아지면 거른 의미가 없다.
    filtering = bool(args.trace or args.user or grep or floor > LEVEL_ORDER["TRACE"])
    showing_previous = not filtering

    for raw in iter(sys.stdin.readline, ""):
        line = raw.rstrip("\n")
        matched = LINE.match(line)

        if not matched:
            if showing_previous and line.strip():
                print(paint("   " + line.strip(), DIM), flush=True)
            continue

        level = matched.group("level")
        trace = matched.group("trace").strip()
        message = matched.group("msg")
        # 이 칸은 `traceId|userId` 한 덩어리다. 거를 때는 반드시 갈라서 본다 — 예전에는 덩어리째
        # 비교해서 `--trace 66f337` 이 영영 아무것도 못 맞췄다(패턴에 userId 가 붙은 뒤로 계속).
        trace_id, _, user_id = (part.strip() for part in trace.partition("|"))

        if LEVEL_ORDER.get(level, 0) < floor:
            showing_previous = False
            continue
        if args.trace and trace_id != args.trace:
            showing_previous = False
            continue
        # 앞부분만 줘도 맞게 한다 — UUID 전문을 매번 붙여넣는 것은 손이 많이 간다.
        if args.user and not user_id.startswith(args.user):
            showing_previous = False
            continue
        if grep and not grep.search(message):
            showing_previous = False
            continue
        showing_previous = True

        color = LEVEL_COLOR.get(level, "")
        cls = short_logger(matched.group("logger"))
        clock = matched.group("ts")[11:23]
        # traceId 가 없는 줄(배치·기동)도 같은 폭을 차지해야 오른쪽 칸이 안 흔들린다.
        chip = paint.trace_chip(trace) if trace else " " * (TRACE_FIELD_WIDTH + 2)
        padding = " " * max(1, LOGGER_WIDTH - len(cls))

        head = (
            paint(GUTTER, color, BOLD)
            + paint(f"{level:<5}", color, BOLD)
            + "  "
            + paint(clock, DIM)
            + "  "
            + chip
            + "  "
            + paint(cls, BOLD)
            + padding
            + paint(f"[{matched.group('thread').strip()}]", DIM)
        )

        if args.compact:
            print(head + "  " + paint_message(message, paint), flush=True)
        else:
            print(head, flush=True)
            print("   " + paint_message(message, paint), flush=True)

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (BrokenPipeError, KeyboardInterrupt):
        # tail -f 를 Ctrl-C 로 끊거나 head 로 자르면 여기로 온다 — 정상 종료다.
        sys.exit(0)
