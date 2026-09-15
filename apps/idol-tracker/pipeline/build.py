"""claims/ と manual/ から docs/data を作り直す（ネット接続・AI不要）。

  python -m pipeline.build
"""
from __future__ import annotations

import yaml

from . import coverage, merge
from .common import CLAIMS_DIR, DOCS_DATA, MANUAL_DIR, load_yaml, read_jsonl, today, write_json


def known_people() -> list[dict]:
    people = {}
    for p in sorted(MANUAL_DIR.glob("*.yaml")):
        doc = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
        people[p.stem] = {"slug": p.stem, "name": doc.get("name", p.stem), "group": doc.get("group")}
    for p in sorted(CLAIMS_DIR.glob("*.jsonl")):
        people.setdefault(p.stem, {"slug": p.stem, "name": p.stem, "group": None})
    return list(people.values())


def _val(person: dict, field: str):
    p = person["profile"].get(field)
    return p["value"] if p else None


def build_all() -> None:
    groups_cfg = load_yaml("groups.yaml")
    index = []
    for info in known_people():
        claims = read_jsonl(CLAIMS_DIR / f"{info['slug']}.jsonl")
        titles = {c["source_url"]: c.get("source_title", "") for c in claims}
        person = merge.build_person(info["slug"], info["name"], claims, titles)
        person["coverage"] = coverage.compute(person)
        write_json(DOCS_DATA / "people" / f"{info['slug']}.json", person)

        current = [m for m in person["memberships"] if not m.get("to")]
        main = next((m for m in current if m["group_id"] == info.get("group")), current[0] if current else None)
        cov = person["coverage"]
        index.append({
            "slug": person["slug"], "name": person["name"],
            "kana": _val(person, "name_kana"), "birthdate": _val(person, "birthdate"),
            "birthplace": _val(person, "birthplace"), "member_color": _val(person, "member_color"),
            "generation": _val(person, "generation"),
            "group_id": main["group_id"] if main else info.get("group"),
            "group": main["group"] if main else groups_cfg.get(info.get("group") or "", {}).get("name"),
            "joined": main.get("from") if main else None,
            "memberships": [{k: m.get(k) for k in ("group_id", "group", "role", "from", "to")}
                            for m in person["memberships"]],
            "coverage": [cov["filled_required"], cov["total_required"]],
            "conflicts": len(cov["conflicts"]),
            "updated": person["updated"],
        })

    groups = []
    for gid, g in groups_cfg.items():
        groups.append({"id": gid, "name": g["name"], "kind": g.get("kind"), "org": g.get("org"),
                       "members": [p["slug"] for p in index
                                   if any(m["group_id"] == gid for m in p["memberships"])]})
    write_json(DOCS_DATA / "index.json", {"updated": today(), "people": index, "groups": groups})
    print(f"built {len(index)} people, {len(groups)} groups")


if __name__ == "__main__":
    build_all()
