"""claims（根拠つきの事実の集まり）から表示用の人物JSONを組み立てる。

毎回 claims 全体から作り直すので、結果は決定的で差分レビューしやすい。
status:
  confirmed  公式ソースあり、または独立した非ファンソース2件以上が一致
  unverified 上記を満たさない
  conflict   非ファンソースに支持された別の値が存在する
"""
from __future__ import annotations

from collections import defaultdict
from difflib import SequenceMatcher

from .common import (EVENT_TYPES, MANUAL_DIR, PROFILE_LIST_FIELDS, classify_url, domain_of,
                     load_yaml, norm_date, norm_text, norm_value, source_id, today)

SRC = load_yaml("sources.yaml")
WEIGHT = SRC["weight"]
RULES = SRC["confirm_rules"]
SINGLE_DATE_TYPES = {"birth", "join", "graduation", "trainee_join", "trainee_leave"}


# ---------- 手入力データ ----------
def load_manual(slug: str) -> tuple[dict, list[dict]]:
    """manual/<slug>.yaml を claims に展開する。"""
    import yaml
    path = MANUAL_DIR / f"{slug}.yaml"
    if not path.exists():
        return {}, []
    doc = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    srcs = {s["id"]: s for s in doc.get("sources", [])}
    claims = []
    for c in doc.get("claims", []):
        for sid in c.get("sources", []):
            row = {k: (str(v) if k in ("date", "end_date", "value") and v is not None else v)
                   for k, v in c.items() if k != "sources"}
            row["source_url"] = srcs[sid]["url"]
            row["extractor"] = "manual"
            claims.append(row)
    return doc, claims


# ---------- 信頼度 ----------
def judge(urls: set[str]) -> tuple[str, int]:
    """(status, score)。status は confirmed / unverified。"""
    types = {u: classify_url(u, SRC) for u in urls}
    if RULES["official_single"] and "official" in types.values():
        status = "confirmed"
    else:
        counted = {domain_of(u) for u, t in types.items() if RULES["fan_counts"] or t != "fan"}
        status = "confirmed" if len(counted) >= RULES["min_independent_domains"] else "unverified"
    score = sum(WEIGHT.get(t, 5) for t in types.values())
    return status, score


def _has_non_fan(urls: set[str]) -> bool:
    return any(classify_url(u, SRC) not in ("fan", "unknown") for u in urls)


def _sids(urls) -> list[str]:
    return sorted(source_id(u) for u in urls)


# ---------- プロフィール ----------
def build_profile(claims: list[dict]) -> dict:
    by_field: dict[str, dict[str, dict]] = defaultdict(dict)
    for c in claims:
        if c.get("kind") != "profile":
            continue
        f = c["field"]
        key = norm_value(f, c["value"])
        slot = by_field[f].setdefault(key, {"values": [], "urls": set(), "manual": False})
        slot["values"].append(c["value"])
        slot["urls"].add(c["source_url"])
        slot["manual"] |= c.get("extractor") == "manual" and bool(c.get("pin"))

    profile = {}
    for field, slots in by_field.items():
        ranked = []
        for key, s in slots.items():
            status, score = judge(s["urls"])
            display = norm_date(s["values"][0]) if field == "birthdate" else min(s["values"], key=len)
            ranked.append({"value": display, "status": status, "score": score + (10_000 if s["manual"] else 0),
                           "urls": s["urls"]})
        ranked.sort(key=lambda r: (-r["score"], str(r["value"])))

        if field in PROFILE_LIST_FIELDS:
            all_urls = set().union(*(r["urls"] for r in ranked))
            status, _ = judge(all_urls)
            profile[field] = {
                "value": [r["value"] for r in ranked],
                "items": [{"value": r["value"], "status": r["status"], "sources": _sids(r["urls"])} for r in ranked],
                "status": status, "sources": _sids(all_urls),
            }
            continue

        top, rest = ranked[0], ranked[1:]
        rivals = [r for r in rest if _has_non_fan(r["urls"])]
        status = top["status"]
        top_official = any(classify_url(u, SRC) == "official" for u in top["urls"])
        rival_official = any(classify_url(u, SRC) == "official" for r in rivals for u in r["urls"])
        if rivals and (not top_official or rival_official):
            status = "conflict"
        entry = {"value": top["value"], "status": status, "sources": _sids(top["urls"])}
        if rest:
            entry["alternatives"] = [{"value": r["value"], "sources": _sids(r["urls"])} for r in rest]
        profile[field] = entry
    return profile


# ---------- 出来事 ----------
def _similar(a: str, b: str) -> float:
    return SequenceMatcher(None, norm_text(a), norm_text(b)).ratio()


def _date_compatible(a: str | None, b: str | None) -> bool:
    """2020 と 2020-11-02 は両立（精度違い）とみなす。"""
    if not a or not b:
        return a == b
    return a.startswith(b) or b.startswith(a)


def build_events(claims: list[dict], group_names: dict[str, str]) -> list[dict]:
    clusters: list[dict] = []
    for c in claims:
        if c.get("kind") != "event":
            continue
        date, label, typ = c.get("date"), c.get("date_label"), c.get("type", "other")
        placed = False
        for cl in clusters:
            if cl["type"] != typ:
                continue
            same_when = _date_compatible(cl["date"], date) and (date or cl["label"] == label)
            if same_when and (typ in SINGLE_DATE_TYPES or _similar(cl["titles"][0], c["title"]) >= 0.35):
                cl["claims"].append(c)
                if date and (not cl["date"] or len(date) > len(cl["date"])):
                    cl["date"] = date
                cl["titles"].append(c["title"])
                placed = True
                break
        if not placed:
            clusters.append({"type": typ, "date": date, "label": label, "titles": [c["title"]], "claims": [c]})

    events = []
    for cl in clusters:
        urls = {c["source_url"] for c in cl["claims"]}
        status, _ = judge(urls)
        best = max(cl["claims"], key=lambda c: (c.get("extractor") == "manual",
                                                WEIGHT.get(classify_url(c["source_url"], SRC), 5),
                                                -len(c["title"])))
        group = best.get("group")
        ev = {
            "date": cl["date"], "date_label": cl["label"] if not cl["date"] else best.get("date_label"),
            "sort": cl["date"] or best.get("sort_hint") or "9999",
            "type": cl["type"], "type_label": EVENT_TYPES.get(cl["type"], "その他"),
            "title": best["title"], "group": group_names.get(group, group) if group else None,
            "status": status, "sources": _sids(urls),
        }
        events.append({k: v for k, v in ev.items() if v is not None})

    # 同じ種類で1回しか起きないはずの出来事に日付違いがあれば矛盾
    by_type = defaultdict(list)
    for e in events:
        if e["type"] in SINGLE_DATE_TYPES and e.get("date"):
            by_type[(e["type"], e.get("group"))].append(e)
    for items in by_type.values():
        if len({i["date"] for i in items}) > 1:
            for i in items:
                i["status"] = "conflict"

    events.sort(key=lambda e: (e["sort"], e.get("date", "")))
    return events


# ---------- 所属 ----------
def build_memberships(claims: list[dict], group_names: dict[str, str]) -> list[dict]:
    by_group = defaultdict(list)
    for c in claims:
        if c.get("kind") == "membership":
            gid = c["group"]
            # グループ名で書かれていたら ID に寄せる
            for k, v in group_names.items():
                if norm_text(v) == norm_text(gid):
                    gid = k
            by_group[gid].append(c)
    out = []
    for gid, cs in by_group.items():
        def pick(key):
            vals = defaultdict(set)
            for c in cs:
                if c.get(key):
                    vals[c[key]].add(c["source_url"])
            if not vals:
                return None, None, set()
            ranked = sorted(vals.items(), key=lambda kv: -judge(kv[1])[1])
            status, _ = judge(ranked[0][1])
            if len({norm_date(v) for v in vals} - {None}) > 1 and all(_has_non_fan(u) for u in vals.values()):
                status = "conflict"
            return ranked[0][0], status, ranked[0][1]
        start, st1, u1 = pick("date")
        end, st2, u2 = pick("end_date")
        roles = [c["role"] for c in cs if c.get("role")]
        statuses = [s for s in (st1, st2) if s]
        status = "conflict" if "conflict" in statuses else ("unverified" if "unverified" in statuses or not statuses else "confirmed")
        out.append({k: v for k, v in {
            "group_id": gid, "group": group_names.get(gid, gid), "role": min(roles, key=len) if roles else None,
            "from": start, "to": end, "status": status,
            "sources": _sids(u1 | u2 | {c["source_url"] for c in cs}),
        }.items() if v is not None})
    out.sort(key=lambda m: m.get("from") or "9999")
    return out


# ---------- SNS ----------
def build_sns(claims: list[dict]) -> list[dict]:
    slots: dict[str, dict] = {}
    for c in claims:
        if c.get("kind") != "sns":
            continue
        key = norm_text(c.get("url") or f'{c.get("service")}:{c.get("handle")}').rstrip("/")
        s = slots.setdefault(key, {"service": c.get("service"), "handle": c.get("handle"),
                                   "url": c.get("url"), "since": c.get("date"), "urls": set()})
        s["urls"].add(c["source_url"])
        for k, ck in (("handle", "handle"), ("url", "url"), ("since", "date"), ("service", "service")):
            s[k] = s[k] or c.get(ck)
    out = []
    for s in slots.values():
        status, _ = judge(s["urls"])
        out.append({k: v for k, v in {"service": s["service"], "handle": s["handle"], "url": s["url"],
                                      "since": s["since"], "status": status,
                                      "sources": _sids(s["urls"])}.items() if v})
    return sorted(out, key=lambda x: x.get("since") or "9999")


# ---------- まとめ ----------
def build_person(slug: str, name: str, claims: list[dict], source_titles: dict[str, str]) -> dict:
    groups = load_yaml("groups.yaml")
    group_names = {k: v["name"] for k, v in groups.items()}
    manual_doc, manual_claims = load_manual(slug)
    all_claims = claims + manual_claims
    for s in manual_doc.get("sources", []):
        source_titles.setdefault(s["url"], s.get("title", ""))

    urls = {c["source_url"] for c in all_claims}
    sources = {}
    for u in sorted(urls):
        sources[source_id(u)] = {"url": u, "title": source_titles.get(u, ""), "type": classify_url(u, SRC),
                                 "domain": domain_of(u)}
    return {
        "slug": slug,
        "name": manual_doc.get("name", name),
        "primary_group": manual_doc.get("group"),
        "updated": today(),
        "profile": build_profile(all_claims),
        "memberships": build_memberships(all_claims, group_names),
        "events": build_events(all_claims, group_names),
        "sns": build_sns(all_claims),
        "sources": sources,
    }
