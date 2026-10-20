"""チェックリスト（config/fields.yaml）に対して何が埋まったかを判定する。"""
from __future__ import annotations

from .common import load_yaml


def compute(person: dict) -> dict:
    cfg = load_yaml("fields.yaml")
    items = []

    for field, spec in cfg["profile"].items():
        p = person["profile"].get(field)
        items.append({"key": f"profile.{field}", "label": spec["label"], "required": spec.get("required", False),
                      "filled": bool(p and p.get("value")), "status": p["status"] if p else None})

    ev_types = {e["type"] for e in person["events"]}
    for key, spec in cfg["career"].items():
        hit = [e for e in person["events"] if e["type"] in spec["event_types"]]
        if key == "join" and any(m.get("from") for m in person["memberships"]):
            hit = hit or [{"status": person["memberships"][0]["status"]}]
        best = ("confirmed" if any(h["status"] == "confirmed" for h in hit)
                else "conflict" if any(h["status"] == "conflict" for h in hit)
                else "unverified" if hit else None)
        items.append({"key": f"career.{key}", "label": spec["label"], "required": spec.get("required", False),
                      "filled": bool(hit), "status": best, "count": len(hit)})
    del ev_types

    sns = person["sns"]
    items.append({"key": "sns", "label": cfg["sns"]["label"], "required": cfg["sns"].get("required", False),
                  "filled": bool(sns), "count": len(sns),
                  "status": "confirmed" if any(s["status"] == "confirmed" for s in sns) else ("unverified" if sns else None)})

    types_present = {s["type"] for s in person["sources"].values()}
    missing_types = [t for t in cfg["required_source_types"] if t not in types_present]

    conflicts = [f"profile.{k}" for k, v in person["profile"].items() if v["status"] == "conflict"]
    conflicts += [f'event:{e.get("date", e.get("date_label"))} {e["title"]}' for e in person["events"] if e["status"] == "conflict"]
    unverified = sum(1 for v in person["profile"].values() if v["status"] == "unverified")
    unverified += sum(1 for e in person["events"] if e["status"] == "unverified")

    required = [i for i in items if i["required"]]
    return {
        "filled_required": sum(1 for i in required if i["filled"]),
        "total_required": len(required),
        "items": items,
        "missing_required": [i["key"] for i in required if not i["filled"]],
        "needs_verification": [i["key"] for i in required if i["filled"] and i["status"] != "confirmed"],
        "missing_source_types": missing_types,
        "conflicts": conflicts,
        "unverified_count": unverified,
        "source_count": len(person["sources"]),
    }


def retry_queries(missing_keys: list[str], name: str, group: str) -> list[str]:
    cfg = load_yaml("fields.yaml")
    qs = []
    for key in missing_keys:
        if key == "sns":
            spec = cfg["sns"]
        else:
            section, field = key.split(".", 1)
            spec = cfg[section][field]
        qs += [q.format(name=name, group=group) for q in spec.get("retry_queries", [])]
    return list(dict.fromkeys(qs))


def report_markdown(person: dict, cov: dict, run: dict) -> str:
    mark = {"confirmed": "●確定", "unverified": "○未確認", "conflict": "▲矛盾", None: "―"}
    lines = [
        f"## {person['name']}（{person['slug']}）",
        "",
        f"- 必須項目: **{cov['filled_required']}/{cov['total_required']}**",
        f"- ソース数: {cov['source_count']} / 未確認: {cov['unverified_count']} / 矛盾: {len(cov['conflicts'])}",
    ]
    if run:
        lines += [f"- 検索クエリ: {run.get('queries', 0)} 件 / 取得ページ: {run.get('fetched', 0)} 件"
                  f"（失敗 {len(run.get('failed', []))}）/ 追加ラウンド: {run.get('retry_rounds', 0)}",
                  f"- AI抽出で引用が本文に無く破棄: {run.get('rejected', 0)} 件"]
    lines += ["", "| 項目 | 状態 | 件数 |", "|---|---|---|"]
    for i in cov["items"]:
        req = "（必須）" if i["required"] else ""
        state = mark[i["status"]] if i["filled"] else ("**未取得**" if i["required"] else "未取得")
        lines.append(f"| {i['label']}{req} | {state} | {i.get('count', '')} |")
    if cov["missing_source_types"]:
        lines += ["", f"⚠️ 取得できていないソース種別: {', '.join(cov['missing_source_types'])}"]
    if cov["conflicts"]:
        lines += ["", "### 矛盾（要確認）"] + [f"- {c}" for c in cov["conflicts"]]
    if run and run.get("failed"):
        lines += ["", "<details><summary>取得に失敗したURL</summary>", ""]
        lines += [f"- {u} … {err}" for u, err in run["failed"][:50]] + ["", "</details>"]
    return "\n".join(lines) + "\n"
