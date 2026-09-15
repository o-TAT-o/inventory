"""検索API。環境変数で使うプロバイダを切り替える。

SEARCH_PROVIDER=brave   BRAVE_API_KEY が必要
SEARCH_PROVIDER=google  GOOGLE_API_KEY と GOOGLE_CSE_ID が必要
SEARCH_PROVIDER=none    検索しない（seed_urls とキャッシュだけで動かす）
"""
from __future__ import annotations

import os
import time

import requests


def _brave(query: str, count: int) -> list[dict]:
    r = requests.get(
        "https://api.search.brave.com/res/v1/web/search",
        params={"q": query, "count": min(count, 20), "search_lang": "jp", "country": "JP"},
        headers={"X-Subscription-Token": os.environ["BRAVE_API_KEY"], "Accept": "application/json"},
        timeout=20,
    )
    r.raise_for_status()
    results = r.json().get("web", {}).get("results", [])
    return [{"url": x["url"], "title": x.get("title", ""), "snippet": x.get("description", "")}
            for x in results]


def _google(query: str, count: int) -> list[dict]:
    out = []
    for start in range(1, min(count, 20) + 1, 10):
        r = requests.get(
            "https://www.googleapis.com/customsearch/v1",
            params={"key": os.environ["GOOGLE_API_KEY"], "cx": os.environ["GOOGLE_CSE_ID"],
                    "q": query, "num": min(10, count - start + 1), "start": start, "lr": "lang_ja"},
            timeout=20,
        )
        r.raise_for_status()
        out += [{"url": x["link"], "title": x.get("title", ""), "snippet": x.get("snippet", "")}
                for x in r.json().get("items", [])]
    return out


def search(query: str, count: int = 10) -> list[dict]:
    provider = os.environ.get("SEARCH_PROVIDER", "brave").lower()
    if provider == "none":
        return []
    fn = {"brave": _brave, "google": _google}[provider]
    for attempt in range(3):
        try:
            results = fn(query, count)
            for x in results:
                x["query"] = query
            time.sleep(1.1)  # 無料枠のレート制限対策
            return results
        except requests.HTTPError as e:
            if e.response is not None and e.response.status_code == 429 and attempt < 2:
                time.sleep(5 * (attempt + 1))
                continue
            raise
    return []
