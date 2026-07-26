#!/usr/bin/env python3
"""OpenAPI(springdoc) 명세를 Notion 페이지에 자동 렌더한다.

코드(`*Api` 어노테이션) → springdoc `/v3/api-docs`(OpenAPI JSON) → 이 스크립트 → Notion 페이지.
CI(dev 머지)와 로컬에서 동일하게 쓴다. 대상 페이지의 기존 블록을 싹 지우고 새로 채우는 방식이라
"직접 수정 금지 · 항상 코드가 진실" 이 유지된다.

환경변수:
  NOTION_TOKEN    Notion internal integration 토큰 (대상 페이지에 Connections 로 공유돼 있어야 함)
  NOTION_PAGE_ID  렌더할 대상 페이지 ID

인자:
  --openapi PATH  OpenAPI JSON 파일 경로 (기본: openapi.json)
  --commit SHA    표기용 커밋 해시 (선택)
  --repo  NAME    표기용 리포 (선택)
"""
import argparse
import datetime
import json
import os
import sys
import urllib.error
import urllib.request

NOTION_VERSION = "2022-06-28"
API = "https://api.notion.com/v1"
# 클라이언트 대상 공개 API 만 렌더한다(어드민 SSR /inventory 등 제외).
PATH_PREFIX = "/api/"
APPEND_CHUNK = 100  # Notion children append 상한


def _req(method, url, token, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Notion-Version", NOTION_VERSION)
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as e:
        sys.exit(f"Notion API {method} {url} 실패: {e.code} {e.read().decode()[:300]}")


# ---- Notion 블록 빌더 ---------------------------------------------------------

def rt(text, code=False, bold=False):
    text = text if text is not None else ""
    return [{"type": "text", "text": {"content": text[:2000]},
             "annotations": {"code": code, "bold": bold}}]


def h1(text):
    return {"object": "block", "type": "heading_1", "heading_1": {"rich_text": rt(text)}}


def h2(text):
    return {"object": "block", "type": "heading_2", "heading_2": {"rich_text": rt(text)}}


def h3(text):
    return {"object": "block", "type": "heading_3", "heading_3": {"rich_text": rt(text)}}


def para(rich):
    return {"object": "block", "type": "paragraph", "paragraph": {"rich_text": rich}}


def bullet(rich):
    return {"object": "block", "type": "bulleted_list_item", "bulleted_list_item": {"rich_text": rich}}


def divider():
    return {"object": "block", "type": "divider", "divider": {}}


def callout(rich, emoji="🤖"):
    return {"object": "block", "type": "callout",
            "callout": {"rich_text": rich, "icon": {"emoji": emoji}}}


def table(rows, has_header=True):
    width = max(len(r) for r in rows)
    children = []
    for r in rows:
        cells = [rt(c) for c in r] + [rt("")] * (width - len(r))
        children.append({"object": "block", "type": "table_row", "table_row": {"cells": cells}})
    return {"object": "block", "type": "table",
            "table": {"table_width": width, "has_column_header": has_header,
                      "has_row_header": False, "children": children}}


# ---- OpenAPI → 렌더 -----------------------------------------------------------

def type_str(schema):
    if schema is None:
        return "—"
    if "$ref" in schema:
        return schema["$ref"].split("/")[-1]
    t = schema.get("type", "object")
    if t == "array":
        return f"{type_str(schema.get('items'))}[]"
    fmt = schema.get("format")
    enum = schema.get("enum")
    if enum:
        return f"enum({'·'.join(map(str, enum))})"
    return f"{t}({fmt})" if fmt else t


def build_blocks(spec, commit, repo, when):
    paths = {p: v for p, v in spec.get("paths", {}).items() if p.startswith(PATH_PREFIX)}
    schemas = spec.get("components", {}).get("schemas", {})
    blocks = [h1("🧭 OffWay API 명세")]

    meta = f"코드(*Api)에서 자동 생성 · 마지막 동기화 {when}"
    if commit:
        meta += f" · commit {commit[:7]}"
    if repo:
        meta += f" · {repo}"
    blocks.append(callout(rt(meta + " — 이 페이지는 직접 수정하지 마세요(dev 머지 시 자동 덮어씀).")))
    blocks.append(divider())

    # 엔드포인트 한눈에
    blocks.append(h2("📚 엔드포인트"))
    overview = [["Method", "Path", "설명"]]
    for path in sorted(paths):
        for method in sorted(paths[path]):
            op = paths[path][method]
            overview.append([method.upper(), path, op.get("summary", "")])
    blocks.append(table(overview))
    blocks.append(divider())

    # 엔드포인트 상세
    for path in sorted(paths):
        for method in sorted(paths[path]):
            op = paths[path][method]
            blocks.append(h3(f"{method.upper()}  {path}"))
            summ = op.get("summary", "")
            desc = op.get("description", "")
            if summ or desc:
                blocks.append(para(rt(" — ".join(x for x in [summ, desc] if x))))

            params = op.get("parameters", [])
            if params:
                blocks.append(para(rt("파라미터", bold=True)))
                for pm in params:
                    req_mark = "필수" if pm.get("required") else "선택"
                    line = f"{pm.get('name')} ({pm.get('in')}, {req_mark}): {type_str(pm.get('schema'))}"
                    blocks.append(bullet(rt(line)))

            body = op.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema")
            if body is not None:
                blocks.append(para(rt("요청 본문", bold=True) + rt(f"  {type_str(body)}", code=True)))

            responses = op.get("responses", {})
            if responses:
                blocks.append(para(rt("응답", bold=True)))
                for code in sorted(responses):
                    blocks.append(bullet(rt(f"{code} — {responses[code].get('description', '')}")))
            blocks.append(divider())

    # 스키마(DTO)
    if schemas:
        blocks.append(h2("🧩 스키마 (DTO)"))
        for name in sorted(schemas):
            props = schemas[name].get("properties", {})
            fields = ", ".join(f"{k}: {type_str(v)}" for k, v in props.items()) or "(필드 없음)"
            blocks.append(bullet(rt(name + " — ", bold=True) + rt(fields)))
    return blocks


# ---- 페이지 갱신(싹 지우고 새로) ------------------------------------------------

def clear_page(page_id, token):
    cursor = None
    while True:
        url = f"{API}/blocks/{page_id}/children?page_size=100"
        if cursor:
            url += f"&start_cursor={cursor}"
        data = _req("GET", url, token)
        for b in data.get("results", []):
            _req("DELETE", f"{API}/blocks/{b['id']}", token)
        if not data.get("has_more"):
            break
        cursor = data.get("next_cursor")


def append_blocks(page_id, token, blocks):
    for i in range(0, len(blocks), APPEND_CHUNK):
        _req("PATCH", f"{API}/blocks/{page_id}/children", token,
             {"children": blocks[i:i + APPEND_CHUNK]})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--openapi", default="openapi.json")
    ap.add_argument("--commit", default=os.environ.get("GITHUB_SHA", ""))
    ap.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", ""))
    args = ap.parse_args()

    token = os.environ.get("NOTION_TOKEN")
    page_id = os.environ.get("NOTION_PAGE_ID")
    if not token or not page_id:
        sys.exit("NOTION_TOKEN·NOTION_PAGE_ID 환경변수가 필요합니다.")

    with open(args.openapi, encoding="utf-8") as f:
        spec = json.load(f)

    when = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).strftime("%Y-%m-%d %H:%M KST")
    blocks = build_blocks(spec, args.commit, args.repo, when)

    print(f"페이지 {page_id} 갱신 — 블록 {len(blocks)}개")
    clear_page(page_id, token)
    append_blocks(page_id, token, blocks)
    print("완료")


if __name__ == "__main__":
    main()
