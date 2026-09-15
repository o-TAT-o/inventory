"""1人分の情報を収集する。

  python -m pipeline.collect --slug rin_kawana --name 川名凜 --group angerme
  python -m pipeline.collect --all            # 既存の全員を更新
  オプション: --no-ai（ルール抽出のみ） --offline（検索もページ取得もしない）

流れ:
  1. 固定URL(seed_urls) ＋ 項目×クエリテンプレートで検索
  2. 本文取得 → ルール抽出 ＋ AI抽出（引用検証つき）
  3. 統合してカバレッジ判定
  4. 必須項目が空なら「取得済みページの再読（項目指定）」→「追加クエリで再検索」を最大N回
  5. claims/<slug>.jsonl と docs/data を更新し、work/report.md を出力
"""
from __future__ import annotations

import argparse
import sys
from collections import Counter

from . import coverage, merge
from .common import (CLAIMS_DIR, MANUAL_DIR, WORK, classify_url, load_yaml, norm_text, read_jsonl,
                     today, write_jsonl)
from .extract import ai_extract, rules_extract, to_claims
from .fetch import fetch
from .search import search

TYPE_PRIORITY = {"official": 0, "news": 1, "encyclopedia": 2, "unknown": 3, "fan": 4}


def _claim_key(c: dict) -> tuple:
    return (c["source_url"], c.get("kind"), c.get("field"), norm_text(c.get("value", "")),
            c.get("date"), c.get("type"), norm_text(c.get("title", "")), c.get("url"), c.get("group"))


def base_queries(name: str, group: str) -> list[str]:
    cfg = load_yaml("fields.yaml")
    qs = [f"{name}", f"{name} {group}"]
    for section in ("profile", "career"):
        for spec in cfg[section].values():
            qs += spec.get("queries", [])
    qs += cfg["sns"].get("queries", [])
    return list(dict.fromkeys(q.format(name=name, group=group) for q in qs))


class Collector:
    def __init__(self, slug: str, name: str, group_id: str, use_ai: bool, offline: bool):
        self.slug, self.name, self.group_id = slug, name, group_id
        self.use_ai, self.offline = use_ai, offline
        self.fields = load_yaml("fields.yaml")
        groups = load_yaml("groups.yaml")
        self.group = groups.get(group_id, {"name": group_id, "seed_urls": []})
        self.claims = read_jsonl(CLAIMS_DIR / f"{slug}.jsonl")
        self.pages: dict[str, dict] = {}
        self.hits: Counter = Counter()
        self.run = {"queries": 0, "fetched": 0, "failed": [], "rejected": 0, "retry_rounds": 0,
                    "blocked_candidates": []}

    # --- 検索 ---
    def discover(self, queries: list[str]) -> list[str]:
        found = []
        for q in queries:
            if self.offline:
                break
            self.run["queries"] += 1
            try:
                results = search(q, self.fields["search_results_per_query"])
            except Exception as e:  # 検索APIの失敗で全体を止めない
                self.run["failed"].append((f"search:{q}", type(e).__name__))
                continue
            for r in results:
                url = r["url"].split("#")[0]
                if classify_url(url) == "blocked":
                    if norm_text(self.name) in norm_text(r["title"] + r["snippet"]):
                        self.run["blocked_candidates"].append(url)
                    continue
                self.hits[url] += 1
                found.append(url)
        return list(dict.fromkeys(found))

    def prioritize(self, urls: list[str]) -> list[str]:
        # 公式→報道→百科→その他→ファン。同じ種別なら多くのクエリに出たURLを優先
        urls = [u for u in urls if u not in self.pages]
        return sorted(urls, key=lambda u: (TYPE_PRIORITY.get(classify_url(u), 3), -self.hits[u]))

    # --- 取得と抽出 ---
    def process(self, url: str, focus: list[str] | None = None) -> None:
        page = self.pages.get(url)
        if page is None:
            if self.offline:
                return
            page = fetch(url)
            self.pages[url] = page
            self.run["fetched"] += 1
            if not page["ok"]:
                self.run["failed"].append((url, page.get("error")))
                return
            facts = rules_extract(page["text"], self.name)
        else:
            facts = []
        if not page["ok"]:
            return
        if self.use_ai:
            try:
                accepted, rejected = ai_extract(page, self.name, self.group["name"], focus)
                facts += accepted
                self.run["rejected"] += len(rejected)
            except Exception as e:
                self.run["failed"].append((url, f"AI抽出失敗 {type(e).__name__}"))
        for c in to_claims(facts, url, today()):
            c["source_title"] = page.get("title", "")
            self.claims.append(c)

    def build(self) -> tuple[dict, dict]:
        uniq = {}
        for c in self.claims:
            uniq.setdefault(_claim_key(c), c)
        self.claims = list(uniq.values())
        titles = {c["source_url"]: c.get("source_title", "") for c in self.claims}
        person = merge.build_person(self.slug, self.name, self.claims, titles)
        return person, coverage.compute(person)

    def main(self) -> tuple[dict, dict]:
        max_pages = self.fields["max_pages_per_person"]
        seeds = [u.format(slug=self.slug, name=self.name) for u in self.group.get("seed_urls", [])]
        urls = seeds + self.prioritize(self.discover(base_queries(self.name, self.group["name"])))
        for url in list(dict.fromkeys(urls))[:max_pages]:
            self.process(url)
        person, cov = self.build()

        for rnd in range(1, self.fields["retry_rounds"] + 1):
            targets = cov["missing_required"] + cov["needs_verification"]
            if not targets and not cov["missing_source_types"]:
                break
            self.run["retry_rounds"] = rnd
            labels = [i["label"] for i in cov["items"] if i["key"] in cov["missing_required"]]
            # a) 信頼できる取得済みページを「足りない項目だけ」指定して読み直す
            if labels and self.use_ai:
                for url, page in list(self.pages.items()):
                    if page["ok"] and classify_url(url) in ("official", "encyclopedia"):
                        self.process(url, focus=labels)
            # b) 追加クエリで再検索
            qs = coverage.retry_queries(cov["missing_required"], self.name, self.group["name"])
            # 未確認の必須項目は、公式・報道で裏付けを取りにいく
            for i in cov["items"]:
                if i["key"] in cov["needs_verification"]:
                    qs.append(f"{self.name} {i['label']} 公式 OR ニュース")
            for t in cov["missing_source_types"]:
                qs.append(f"{self.name} {self.group['name']} " + {"news": "ニュース", "official": "公式",
                                                                  "encyclopedia": "wikipedia"}.get(t, ""))
            new = self.prioritize(self.discover(qs))
            budget = max(5, max_pages // 4)
            for url in new[:budget]:
                self.process(url)
            person, cov = self.build()

        return person, cov


def save(slug: str, claims: list[dict], person: dict, cov: dict) -> None:
    write_jsonl(CLAIMS_DIR / f"{slug}.jsonl", sorted(claims, key=lambda c: (c["source_url"], str(_claim_key(c)))))


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--slug")
    ap.add_argument("--name")
    ap.add_argument("--group", default="angerme")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--no-ai", action="store_true")
    ap.add_argument("--offline", action="store_true")
    args = ap.parse_args(argv)

    targets = []
    if args.all:
        from .build import known_people
        targets = [(p["slug"], p["name"], p.get("group") or args.group) for p in known_people()]
    elif args.slug and args.name:
        targets = [(args.slug, args.name, args.group)]
    else:
        ap.error("--slug と --name、または --all を指定してください")

    reports = []
    for slug, name, group in targets:
        col = Collector(slug, name, group, use_ai=not args.no_ai, offline=args.offline)
        person, cov = col.main()
        save(slug, col.claims, person, cov)
        if not (MANUAL_DIR / f"{slug}.yaml").exists():
            (MANUAL_DIR).mkdir(exist_ok=True)
            (MANUAL_DIR / f"{slug}.yaml").write_text(
                f"name: {name}\ngroup: {group}\nsources: []\nclaims: []\n", encoding="utf-8")
        md = coverage.report_markdown(person, cov, col.run)
        if col.run["blocked_candidates"]:
            md += "\n### SNS候補（本文は取得していません。公式か手動で確認してください）\n"
            md += "\n".join(f"- {u}" for u in dict.fromkeys(col.run["blocked_candidates"])) + "\n"
        reports.append(md)
        print(md)

    from .build import build_all
    build_all()
    WORK.mkdir(exist_ok=True)
    (WORK / "report.md").write_text("# 収集レポート\n\n" + "\n---\n".join(reports), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
