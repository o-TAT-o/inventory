"""ページ本文から事実（claim）を抜き出す。

2系統で抽出して取りこぼしを減らす:
  1. rules_extract: 正規表現。AIを使わないので「AIが見落とした」を検出できる
  2. ai_extract:    Claude。引用(quote)が本文に実在しない事実は破棄する
"""
from __future__ import annotations

import json
import os
import re
import unicodedata

from .common import EVENT_TYPES, ROOT, norm_date, norm_text
from .fetch import chunks

PROMPT = (ROOT / "pipeline" / "prompts" / "extract.md").read_text(encoding="utf-8")
MODEL = os.environ.get("EXTRACT_MODEL", "claude-haiku-4-5-20251001")

TOOL = {
    "name": "record_facts",
    "description": "ページから抽出した事実を記録する",
    "input_schema": {
        "type": "object",
        "properties": {
            "is_about_target": {"type": "boolean"},
            "facts": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "kind": {"enum": ["profile", "membership", "event", "sns"]},
                        "field": {"type": "string"},
                        "value": {"type": "string"},
                        "group": {"type": ["string", "null"]},
                        "role": {"type": ["string", "null"]},
                        "date": {"type": ["string", "null"]},
                        "end_date": {"type": ["string", "null"]},
                        "date_label": {"type": ["string", "null"]},
                        "type": {"type": ["string", "null"]},
                        "title": {"type": ["string", "null"]},
                        "service": {"type": ["string", "null"]},
                        "handle": {"type": ["string", "null"]},
                        "url": {"type": ["string", "null"]},
                        "quote": {"type": "string"},
                    },
                    "required": ["kind", "quote"],
                },
            },
        },
        "required": ["is_about_target", "facts"],
    },
}


# ---------- 1. ルールベース ----------
_RULES = [
    ("birthdate", re.compile(r"生年月日[\s:：|]*(\d{4}年\s*\d{1,2}月\s*\d{1,2}日)")),
    ("birthplace", re.compile(r"出身地?[\s:：|]*([^\s、。|（(]{2,4}?[都道府県])")),
    ("blood_type", re.compile(r"血液型[\s:：|]*(AB|A|B|O)型?")),
    ("member_color", re.compile(r"メンバーカラー[\s:：|はが]*([^\s、。|（(]{1,10})")),
]


def rules_extract(text: str, name: str) -> list[dict]:
    """プロフィール表の定型項目だけを拾う。本人名がページに無ければ何もしない。"""
    t = unicodedata.normalize("NFKC", text)
    if norm_text(name) not in norm_text(t):
        return []
    n = re.escape(unicodedata.normalize("NFKC", name))
    # 本文型：「<名前>は2003年12月6日生まれ、千葉県出身」（名前の近くにある場合のみ）
    rules = _RULES + [
        ("birthdate", re.compile(n + r"[^。]{0,20}?(\d{4}年\d{1,2}月\d{1,2}日)\s*生まれ")),
        ("birthplace", re.compile(n + r"[^。]{0,40}?([^\s、。,（(]{2,3}?[都道府県])出身")),
    ]
    out, seen = [], set()
    for field, pat in rules:
        if field in seen:
            continue
        m = pat.search(t)
        if m:
            seen.add(field)
            out.append({"kind": "profile", "field": field, "value": m.group(1).replace(" ", ""),
                        "quote": m.group(0)[-60:], "extractor": "rules"})
    return out


# ---------- 2. AI ----------
def _quote_ok(quote: str, text: str) -> bool:
    q = norm_text(quote)
    return len(q) >= 4 and q in norm_text(text)


def _clean(fact: dict) -> dict | None:
    kind = fact.get("kind")
    f = {k: v for k, v in fact.items() if v not in (None, "")}
    for k in ("date", "end_date"):
        if k in f:
            nd = norm_date(f[k])
            if nd:
                f[k] = nd
            else:
                f.setdefault("date_label", f[k]) if k == "date" else None
                f.pop(k)
    if kind == "profile" and not (f.get("field") and f.get("value")):
        return None
    if kind == "event":
        if f.get("type") not in EVENT_TYPES:
            f["type"] = "other"
        if not f.get("title") or not (f.get("date") or f.get("date_label")):
            return None
    if kind == "membership" and not f.get("group"):
        return None
    if kind == "sns" and not (f.get("url") or f.get("handle")):
        return None
    f["extractor"] = "ai"
    return f


def ai_extract(page: dict, name: str, group_name: str, focus_fields: list[str] | None = None) -> tuple[list[dict], list[dict]]:
    """(採用した事実, 引用が見つからず破棄した事実) を返す。"""
    import anthropic  # 遅延 import（テストやAIなし実行で不要にするため）

    client = anthropic.Anthropic()
    focus = ""
    if focus_fields:
        focus = ("7. 今回は取りこぼし確認です。特に次の項目が書かれていないか、本文の隅々まで探してください: "
                 + "、".join(focus_fields))
    system = PROMPT.format(name=name, group_name=group_name, url=page["url"], focus=focus,
                           event_types=", ".join(f"{k}({v})" for k, v in EVENT_TYPES.items()))
    accepted, rejected = [], []
    for part in chunks(page["text"]):
        if norm_text(name) not in norm_text(part) and norm_text(name[:2]) not in norm_text(part):
            continue  # 本人が出てこない分割は読まない（コスト削減）
        resp = client.messages.create(
            model=MODEL, max_tokens=4000, system=system,
            tools=[TOOL], tool_choice={"type": "tool", "name": "record_facts"},
            messages=[{"role": "user", "content": f"<page>\n{part}\n</page>"}],
        )
        block = next((b for b in resp.content if b.type == "tool_use"), None)
        if not block or not block.input.get("is_about_target"):
            continue
        for raw in block.input.get("facts", []):
            fact = _clean(raw)
            if fact is None:
                continue
            (accepted if _quote_ok(fact.get("quote", ""), part) else rejected).append(fact)
    return accepted, rejected


def to_claims(facts: list[dict], url: str, run_date: str) -> list[dict]:
    rows = []
    for f in facts:
        c = dict(f)
        c["source_url"] = url
        c["collected"] = run_date
        rows.append(c)
    return rows


if __name__ == "__main__":  # 手動テスト: python -m pipeline.extract < page.txt
    import sys
    print(json.dumps(rules_extract(sys.stdin.read(), sys.argv[1]), ensure_ascii=False, indent=2))
