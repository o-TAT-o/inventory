"""共通処理：パス、設定読み込み、URL分類、値の正規化。"""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import re
import unicodedata
from pathlib import Path
from urllib.parse import urlparse

import yaml

ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / "config"
DOCS_DATA = ROOT / "docs" / "data"
CLAIMS_DIR = ROOT / "claims"      # AI/ルール抽出の結果（根拠つき）。レビュー用に git 管理
MANUAL_DIR = ROOT / "manual"      # 手入力データ。自動収集より優先
WORK = ROOT / "work"              # キャッシュ・レポート。git 管理しない

EVENT_TYPES = {
    "birth": "誕生", "school": "学生時代", "pre": "加入前の活動", "audition": "オーディション",
    "trainee_join": "研修生加入", "trainee_leave": "研修生修了", "join": "加入",
    "graduation": "卒業", "release": "リリース", "appearance": "出演", "media": "メディア",
    "stage": "舞台", "appointment": "就任", "sns": "SNS", "other": "その他",
}
PROFILE_LIST_FIELDS = {"nicknames", "hobbies"}


def load_yaml(name: str) -> dict:
    with open(CONFIG / name, encoding="utf-8") as f:
        return yaml.safe_load(f)


def today() -> str:
    return dt.date.today().isoformat()


def source_id(url: str) -> str:
    return "s" + hashlib.sha1(url.encode("utf-8")).hexdigest()[:8]


def classify_url(url: str, sources_cfg: dict | None = None) -> str:
    """URL をソース種別（official/news/encyclopedia/fan/blocked/unknown）に分類する。"""
    cfg = sources_cfg or load_yaml("sources.yaml")
    p = urlparse(url)
    host = p.netloc.lower().removeprefix("www.")
    target = host + p.path
    best, best_len = "unknown", -1
    for typ, patterns in cfg["types"].items():
        for pat in patterns:
            pat = pat.lower()
            hit = (target.startswith(pat) if "/" in pat
                   else host == pat or host.endswith("." + pat))
            if hit and len(pat) > best_len:
                best, best_len = typ, len(pat)
    return best


def domain_of(url: str) -> str:
    host = urlparse(url).netloc.lower().removeprefix("www.")
    # ameblo 等はブログ単位で独立ソースとみなす
    if host == "ameblo.jp":
        first = urlparse(url).path.strip("/").split("/")[0]
        return f"{host}/{first}"
    return host


def norm_text(s: str) -> str:
    s = unicodedata.normalize("NFKC", str(s))
    return re.sub(r"\s+", "", s).lower()


_DATE_PATTERNS = [
    (re.compile(r"(\d{4})[年/.\-](\d{1,2})[月/.\-](\d{1,2})日?"), "ymd"),
    (re.compile(r"(\d{4})[年/.\-](\d{1,2})月?"), "ym"),
    (re.compile(r"(\d{4})年?"), "y"),
]


def norm_date(s: str | None) -> str | None:
    """「2020年11月2日」「2020/11/2」「2020-11」「2020」を ISO 形式（精度そのまま）に。"""
    if not s:
        return None
    s = unicodedata.normalize("NFKC", str(s)).strip()
    for pat, kind in _DATE_PATTERNS:
        m = pat.fullmatch(s) or pat.match(s)
        if m:
            y = int(m.group(1))
            if not 1950 <= y <= 2100:
                return None
            if kind == "ymd":
                return f"{y:04d}-{int(m.group(2)):02d}-{int(m.group(3)):02d}"
            if kind == "ym":
                return f"{y:04d}-{int(m.group(2)):02d}"
            return f"{y:04d}"
    return None


def norm_value(field: str, value) -> str:
    """比較用のキー。表示には元の値を使う。"""
    if field in {"birthdate"}:
        return norm_date(value) or norm_text(value)
    v = norm_text(value)
    if field == "birthplace":
        v = re.sub(r"(都|道|府|県)$", "", v) if v not in {"北海道"} else v
    if field == "blood_type":
        v = v.replace("型", "")
    if field == "member_color":
        v = {"緑": "グリーン", "green": "グリーン"}.get(v, v)
    return v


def read_json(path: Path, default=None):
    if not path.exists():
        return default
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False, sort_keys=True) + "\n")
