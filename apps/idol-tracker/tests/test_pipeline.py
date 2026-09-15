"""python -m unittest discover tests"""
import unittest

from pipeline import merge
from pipeline.common import classify_url, norm_date, norm_value
from pipeline.extract import _clean, _quote_ok, rules_extract

OFFICIAL = "https://www.helloproject.com/angerme/profile/x/"
WIKI = "https://ja.wikipedia.org/wiki/X"
NEWS = "https://natalie.mu/music/news/1"
FAN = "https://dic.pixiv.net/a/X"
FAN2 = "https://seesaawiki.jp/x/"


def prof(field, value, url):
    return {"kind": "profile", "field": field, "value": value, "source_url": url}


class CommonTest(unittest.TestCase):
    def test_norm_date(self):
        self.assertEqual(norm_date("2020年11月2日"), "2020-11-02")
        self.assertEqual(norm_date("2020/4"), "2020-04")
        self.assertEqual(norm_date("２０２０年"), "2020")
        self.assertIsNone(norm_date("中学時代"))

    def test_classify(self):
        self.assertEqual(classify_url(OFFICIAL), "official")
        self.assertEqual(classify_url("https://ameblo.jp/angerme-new/entry-1.html"), "official")
        self.assertEqual(classify_url("https://ameblo.jp/someone/entry-1.html"), "unknown")
        self.assertEqual(classify_url("https://x.com/abc"), "blocked")

    def test_norm_value(self):
        self.assertEqual(norm_value("birthplace", "千葉県"), norm_value("birthplace", "千葉"))
        self.assertEqual(norm_value("blood_type", "B型"), norm_value("blood_type", "B"))


class JudgeTest(unittest.TestCase):
    def test_official_single_confirms(self):
        self.assertEqual(merge.judge({OFFICIAL})[0], "confirmed")

    def test_two_independent_confirm(self):
        self.assertEqual(merge.judge({WIKI, NEWS})[0], "confirmed")

    def test_fan_does_not_count(self):
        self.assertEqual(merge.judge({WIKI, FAN})[0], "unverified")
        self.assertEqual(merge.judge({FAN, FAN2})[0], "unverified")


class ProfileTest(unittest.TestCase):
    def test_conflict_between_non_fan_sources(self):
        p = merge.build_profile([prof("birthplace", "千葉県", WIKI), prof("birthplace", "東京都", NEWS)])
        self.assertEqual(p["birthplace"]["status"], "conflict")
        self.assertEqual(len(p["birthplace"]["alternatives"]), 1)

    def test_official_beats_fan_without_conflict(self):
        p = merge.build_profile([prof("birthplace", "千葉県", OFFICIAL), prof("birthplace", "東京都", FAN)])
        self.assertEqual(p["birthplace"]["value"], "千葉県")
        self.assertEqual(p["birthplace"]["status"], "confirmed")

    def test_equivalent_values_merge(self):
        p = merge.build_profile([prof("birthplace", "千葉県", WIKI), prof("birthplace", "千葉", NEWS)])
        self.assertEqual(p["birthplace"]["status"], "confirmed")
        self.assertNotIn("alternatives", p["birthplace"])


class EventTest(unittest.TestCase):
    def ev(self, date, title, url, typ="join"):
        return {"kind": "event", "type": typ, "date": date, "title": title, "source_url": url}

    def test_precision_merge(self):
        evs = merge.build_events([self.ev("2020", "加入", FAN), self.ev("2020-11-02", "アンジュルム加入", WIKI),
                                  self.ev("2020-11-02", "加入発表", NEWS)], {})
        self.assertEqual(len(evs), 1)
        self.assertEqual(evs[0]["date"], "2020-11-02")
        self.assertEqual(evs[0]["status"], "confirmed")

    def test_join_date_conflict(self):
        evs = merge.build_events([self.ev("2020-11-02", "加入", WIKI), self.ev("2021-01-01", "加入", NEWS)], {})
        self.assertTrue(all(e["status"] == "conflict" for e in evs))

    def test_different_titles_stay_separate(self):
        evs = merge.build_events([self.ev("2022-08-06", "舞台ゲスト出演", WIKI, "stage"),
                                  self.ev("2022-08-06", "ラジオ番組にコメント", NEWS, "stage")], {})
        self.assertEqual(len(evs), 2)


class ExtractTest(unittest.TestCase):
    def test_quote_must_exist(self):
        text = "川名凜は2003年12月6日生まれ、千葉県出身。"
        self.assertTrue(_quote_ok("2003年12月6日生まれ", text))
        self.assertFalse(_quote_ok("東京都出身", text))

    def test_rules(self):
        text = "川名凜\n生年月日 2003年12月6日\n血液型 B型\n出身地 千葉県\n"
        got = {c["field"]: c["value"] for c in rules_extract(text, "川名凜")}
        self.assertEqual(got["birthdate"], "2003年12月6日")
        self.assertEqual(got["blood_type"], "B")
        self.assertEqual(got["birthplace"], "千葉県")
        self.assertEqual(rules_extract(text, "別の人"), [])

    def test_clean_drops_undated_event(self):
        self.assertIsNone(_clean({"kind": "event", "title": "何か", "quote": "x"}))
        c = _clean({"kind": "event", "title": "加入", "date": "2020年11月2日", "type": "join", "quote": "x"})
        self.assertEqual(c["date"], "2020-11-02")


if __name__ == "__main__":
    unittest.main()
