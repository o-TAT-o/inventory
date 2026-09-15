"""ページ本文の取得。robots.txt を守り、結果は work/cache にキャッシュする。"""
from __future__ import annotations

import hashlib
import html
import re
import time
from urllib import robotparser
from urllib.parse import urlparse

import requests

from .common import WORK, read_json, write_json

UA = "oshi-nenpyo-bot/0.1 (+personal research; respects robots.txt)"
CACHE = WORK / "cache"
_robots: dict[str, robotparser.RobotFileParser | None] = {}
_last_hit: dict[str, float] = {}


def _allowed(url: str) -> bool:
    p = urlparse(url)
    base = f"{p.scheme}://{p.netloc}"
    if base not in _robots:
        rp = robotparser.RobotFileParser()
        try:
            r = requests.get(base + "/robots.txt", headers={"User-Agent": UA}, timeout=10)
            rp.parse(r.text.splitlines() if r.status_code == 200 else [])
        except requests.RequestException:
            rp.parse([])
        _robots[base] = rp
    return _robots[base].can_fetch(UA, url)


def _to_text(raw_html: str) -> str:
    try:
        import trafilatura  # 本文抽出の精度が高い
        text = trafilatura.extract(raw_html, include_tables=True, include_links=False, favor_recall=True)
        if text and len(text) > 200:
            return text
    except ImportError:
        pass
    # フォールバック：タグを落とすだけ
    s = re.sub(r"(?is)<(script|style|noscript).*?</\1>", " ", raw_html)
    s = re.sub(r"(?i)<br\s*/?>|</(p|div|tr|li|h\d|dt|dd)>", "\n", s)
    s = re.sub(r"<[^>]+>", " ", s)
    s = html.unescape(s)
    return re.sub(r"[ \t\u3000]+", " ", re.sub(r"\n\s*\n+", "\n", s)).strip()


def fetch(url: str, max_age_days: int = 7) -> dict:
    """{'url','ok','status','text','title','fetched_at','error'} を返す。"""
    key = hashlib.sha1(url.encode()).hexdigest()
    path = CACHE / f"{key}.json"
    cached = read_json(path)
    if cached and time.time() - cached.get("fetched_ts", 0) < max_age_days * 86400:
        return cached

    result = {"url": url, "ok": False, "status": None, "text": "", "title": "",
              "fetched_ts": time.time(), "error": None}
    if not _allowed(url):
        result["error"] = "robots.txt で禁止"
        write_json(path, result)
        return result

    host = urlparse(url).netloc
    wait = 2.0 - (time.time() - _last_hit.get(host, 0))
    if wait > 0:
        time.sleep(wait)  # 同一ホストへは2秒間隔
    try:
        r = requests.get(url, headers={"User-Agent": UA}, timeout=25)
        _last_hit[host] = time.time()
        result["status"] = r.status_code
        if r.status_code == 200 and "html" in r.headers.get("content-type", "html"):
            r.encoding = r.apparent_encoding or r.encoding
            m = re.search(r"(?is)<title>(.*?)</title>", r.text)
            result["title"] = html.unescape(m.group(1)).strip()[:200] if m else ""
            result["text"] = _to_text(r.text)
            result["ok"] = bool(result["text"])
        else:
            result["error"] = f"HTTP {r.status_code}"
    except requests.RequestException as e:
        result["error"] = type(e).__name__
    write_json(path, result)
    return result


def chunks(text: str, size: int = 6000, overlap: int = 400) -> list[str]:
    """長いページを重なりつきで分割（1回で読ませると抽出漏れが増えるため）。"""
    if len(text) <= size:
        return [text]
    out, i = [], 0
    while i < len(text):
        out.append(text[i:i + size])
        i += size - overlap
    return out
