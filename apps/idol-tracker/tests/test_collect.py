"""検索・取得・AIをモックして、再検索ラウンドが動くことを確認する。"""
import unittest
from unittest import mock

from pipeline import collect

WIKI = "https://ja.wikipedia.org/wiki/テスト花子"
NEWS = "https://natalie.mu/music/news/999"
PAGES = {
    WIKI: "テスト花子は2004年1月2日生まれ、東京都出身。2021年5月1日にサンプル組に加入した。",
    NEWS: "サンプル組の新メンバーとしてテスト花子が加入。愛称は「はなちゃん」。",
}


def fake_search(query, count=10):
    if "愛称" in query or "ニックネーム" in query:
        return [{"url": NEWS, "title": "テスト花子 加入", "snippet": "", "query": query}]
    return [{"url": WIKI, "title": "テスト花子", "snippet": "", "query": query},
            {"url": "https://x.com/hanako", "title": "テスト花子 公式", "snippet": "", "query": query}]


def fake_fetch(url, max_age_days=7):
    return {"url": url, "ok": url in PAGES, "text": PAGES.get(url, ""), "title": url, "error": None}


def fake_ai(page, name, group_name, focus=None):
    facts = []
    if page["url"] == WIKI:
        facts = [
            {"kind": "event", "type": "join", "date": "2021-05-01", "title": "サンプル組加入", "quote": "2021年5月1日にサンプル組に加入"},
            {"kind": "profile", "field": "nicknames", "value": "はなこ", "quote": "これは本文に無い引用"},
        ]
    if page["url"] == NEWS:
        facts = [{"kind": "profile", "field": "nicknames", "value": "はなちゃん", "quote": "愛称は「はなちゃん」"}]
    text = page["text"]
    ok = [f for f in facts if f["quote"] in text]
    return ok, [f for f in facts if f["quote"] not in text]


class CollectFlowTest(unittest.TestCase):
    @mock.patch.object(collect, "search", side_effect=fake_search)
    @mock.patch.object(collect, "fetch", side_effect=fake_fetch)
    @mock.patch.object(collect, "ai_extract", side_effect=fake_ai)
    @mock.patch("pipeline.merge.load_manual", return_value=({}, []))
    def test_retry_finds_missing_nickname(self, *_):
        col = collect.Collector("test_hanako", "テスト花子", "unknown_group", use_ai=True, offline=False)
        col.claims = []
        col.group = {"name": "サンプル組", "seed_urls": []}
        person, cov = col.main()
        self.assertEqual(person["profile"]["birthdate"]["value"], "2004-01-02")    # ルール抽出
        self.assertIn("はなちゃん", person["profile"]["nicknames"]["value"])         # 追加ラウンドで取得
        self.assertNotIn("はなこ", person["profile"]["nicknames"]["value"])          # 引用なしは破棄
        self.assertGreaterEqual(col.run["rejected"], 1)
        self.assertIn("https://x.com/hanako", col.run["blocked_candidates"])        # SNSは本文取得しない
        self.assertGreaterEqual(col.run["retry_rounds"], 1)


if __name__ == "__main__":
    unittest.main()
