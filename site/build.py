#!/usr/bin/env python3
"""GitHub Pages に配信するサイトを組み立てる。

site/pages.json に書いたページを <出力先>/<slug>/ にコピーし、
ホーム（<出力先>/index.html）と共通ナビ（<出力先>/_site-nav/site-nav.js）を作って、
各ページの HTML に共通ナビを差し込む。元のファイルは書き換えない。

使い方（リポジトリ直下で）:
    python3 site/build.py              # _site に出力
    python3 site/build.py --out /tmp/site
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import shutil
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

SITE_DIR = Path(__file__).resolve().parent
REPO_ROOT = SITE_DIR.parent
NAV_DIR = "_site-nav"
NAV_MARKER = "/*__SITE_DATA__*/ null"
SLUG_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")
RESERVED_SLUGS = {NAV_DIR, "index", "404"}
# ページのフォルダからコピーしないもの（サイトには不要な説明ファイルなど）
SKIP_PATTERNS = ("README.md", "CLAUDE.md", "AGENTS.md", ".*")
BODY_OPEN_RE = re.compile(r"<body\b[^>]*>", re.IGNORECASE)
HEAD_CLOSE_RE = re.compile(r"</head\s*>", re.IGNORECASE)
# スクリプトが動くまでの間もナビの高さぶんを確保して、表示がずれないようにする
NAV_ELEMENT = '<site-nav style="display:block;min-height:45px"></site-nav>'
JST = timezone(timedelta(hours=9))


@dataclass(frozen=True)
class Page:
    """公開するページ 1 件分の設定。"""

    slug: str
    src: Path
    title: str
    description: str
    category: str
    listed: bool
    nav: bool


def fail(message: str) -> None:
    raise SystemExit(f"build.py: {message}")


def load_manifest(path: Path) -> tuple[dict[str, str], list[Page]]:
    """pages.json を読み、設定の誤りがあれば止める。"""
    data = json.loads(path.read_text(encoding="utf-8"))
    site = {
        "title": str(data["site"]["title"]),
        "description": str(data["site"].get("description", "")),
    }
    pages: list[Page] = []
    seen: set[str] = set()
    for raw in data["pages"]:
        slug = str(raw["slug"])
        if not SLUG_RE.match(slug) or slug in RESERVED_SLUGS:
            fail(f"slug「{slug}」は使えません（半角英小文字・数字・ハイフンのみ。予約語: {sorted(RESERVED_SLUGS)}）")
        if slug in seen:
            fail(f"slug「{slug}」が重複しています")
        seen.add(slug)
        src = (REPO_ROOT / raw["src"]).resolve()
        if REPO_ROOT not in src.parents:
            fail(f"src「{raw['src']}」はリポジトリの中を指してください")
        if not src.is_dir():
            fail(f"src「{raw['src']}」のフォルダが見つかりません")
        pages.append(
            Page(
                slug=slug,
                src=src,
                title=str(raw["title"]),
                description=str(raw.get("description", "")),
                category=str(raw.get("category", "")) or "その他",
                listed=bool(raw.get("listed", True)),
                nav=bool(raw.get("nav", True)),
            )
        )
    return site, pages


def prepare_out(out: Path) -> None:
    """出力先を空にする。リポジトリそのものを消さないよう確認してから。"""
    if out == REPO_ROOT or out in REPO_ROOT.parents or out == SITE_DIR or SITE_DIR in out.parents:
        fail(f"出力先 {out} には書き出せません")
    if (out / ".git").exists():
        fail(f"出力先 {out} は git リポジトリのようです")
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)


def write_nav(site: dict[str, str], pages: list[Page], out: Path) -> None:
    """ページ一覧を埋め込んだ共通ナビのスクリプトを書き出す。"""
    template = (SITE_DIR / "site-nav.js").read_text(encoding="utf-8")
    if NAV_MARKER not in template:
        fail("site-nav.js に埋め込み位置（/*__SITE_DATA__*/ null）がありません")
    data = {
        "site": site,
        "pages": [
            {
                "slug": p.slug,
                "title": p.title,
                "description": p.description,
                "category": p.category,
                "listed": p.listed,
            }
            for p in pages
        ],
    }
    payload = json.dumps(data, ensure_ascii=False).replace("</", "<\\/")
    (out / NAV_DIR).mkdir()
    (out / NAV_DIR / "site-nav.js").write_text(template.replace(NAV_MARKER, payload), encoding="utf-8")


def render_home(site: dict[str, str], pages: list[Page], out: Path) -> None:
    """pages.json の内容からホームの index.html を作る。"""
    groups: dict[str, list[Page]] = {}
    for page in pages:
        if page.listed:
            groups.setdefault(page.category, []).append(page)

    sections: list[str] = []
    for i, (name, items) in enumerate(groups.items(), start=1):
        stops = "\n".join(
            f'      <li class="stop"><span class="st" aria-hidden="true"></span>'
            f'<a class="card" href="{html.escape(p.slug)}/">'
            f'<span class="t">{html.escape(p.title)}</span>'
            f'<span class="d">{html.escape(p.description)}</span>'
            f'<span class="go">開く →</span></a></li>'
            for p in items
        )
        sections.append(
            f'  <section class="group" aria-labelledby="g{i}">\n'
            f'    <h2 id="g{i}">{html.escape(name)}</h2>\n'
            f'    <ol class="line">\n{stops}\n    </ol>\n'
            f"  </section>"
        )

    built_at = datetime.now(JST).strftime("%Y/%m/%d %H:%M")
    footer = [f"<span>{built_at}（日本時間）に更新</span>"]
    server = os.environ.get("GITHUB_SERVER_URL")
    repo = os.environ.get("GITHUB_REPOSITORY")
    if server and repo:
        footer.append(f'<a href="{html.escape(server)}/{html.escape(repo)}">GitHubのリポジトリ</a>')

    text = (SITE_DIR / "home.html").read_text(encoding="utf-8")
    for key, value in {
        "{{SITE_TITLE}}": html.escape(site["title"]),
        "{{SITE_DESCRIPTION}}": html.escape(site["description"]),
        "{{SECTIONS}}": "\n".join(sections),
        "{{FOOTER}}": "".join(footer),
    }.items():
        text = text.replace(key, value)
    (out / "index.html").write_text(text, encoding="utf-8")


def inject_nav(html_path: Path, out: Path) -> bool:
    """HTML に共通ナビの要素とスクリプトを差し込む。差し込んだら True。"""
    text = html_path.read_text(encoding="utf-8")
    if "<site-nav" in text:
        return False
    body = BODY_OPEN_RE.search(text)
    head = HEAD_CLOSE_RE.search(text)
    if not body or not head or head.start() > body.start():
        print(f"  ! {html_path.relative_to(out)}: <head> か <body> が見つからないので、ナビを差し込みませんでした")
        return False
    rel = os.path.relpath(out / NAV_DIR / "site-nav.js", html_path.parent).replace(os.sep, "/")
    # 後ろ（body）から先に差し込むと、head の位置がずれない
    text = text[: body.end()] + "\n" + NAV_ELEMENT + text[body.end() :]
    text = text[: head.start()] + f'<script src="{rel}" defer></script>\n' + text[head.start() :]
    html_path.write_text(text, encoding="utf-8")
    return True


def build(out: Path) -> None:
    site, pages = load_manifest(SITE_DIR / "pages.json")
    prepare_out(out)
    for page in pages:
        shutil.copytree(page.src, out / page.slug, ignore=shutil.ignore_patterns(*SKIP_PATTERNS))
        print(f"  {page.src.relative_to(REPO_ROOT)} → /{page.slug}/")
    write_nav(site, pages, out)
    render_home(site, pages, out)
    (out / ".nojekyll").write_text("", encoding="utf-8")

    no_nav = {p.slug for p in pages if not p.nav}
    injected = 0
    for html_path in sorted(out.rglob("*.html")):
        parts = html_path.relative_to(out).parts
        if parts[0] == NAV_DIR or (len(parts) > 1 and parts[0] in no_nav):
            continue
        if inject_nav(html_path, out):
            injected += 1
    print(f"ページ {len(pages)} 件、共通ナビを {injected} 個の HTML に差し込みました → {out}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="GitHub Pages に配信するサイトを組み立てる")
    parser.add_argument("--out", default=str(REPO_ROOT / "_site"), help="出力先（既定: リポジトリ直下の _site）")
    args = parser.parse_args(argv)
    build(Path(args.out).resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
