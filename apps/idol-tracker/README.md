# 推し年表

アイドルのプロフィールと活動記録を、出典つきの表と年表で見るための個人用サイトです。
表示は GitHub Pages、情報収集は GitHub Actions で動くので、サーバーは要りません。

## しくみ

```
GitHub Actions（手動 or 毎週月曜）
  1. 検索     固定URL ＋「項目 × クエリテンプレート」で検索API
  2. 取得     robots.txt を守って本文を取得（X/Instagram等は本文を取らない）
  3. 抽出     ルール抽出（正規表現）＋ AI抽出（Claude）
              AIの結果は「引用が本文に実在するか」を機械チェックし、無ければ破棄
  4. 統合     値ごとに出典を束ね、確定／未確認／矛盾を判定
  5. 点検     チェックリストに対して空欄・未確認・足りないソース種別を判定
  6. 再探索   空欄や未確認があれば、取得済みの公式・百科ページを項目指定で読み直し、
              追加クエリで再検索（最大2ラウンド）
  7. PR作成   収集レポートを本文にしたプルリクエストが届く → 確認してマージ
        ↓
GitHub Pages（docs/）が docs/data/*.json を読んで表示
```

AIは「どこを探すか」には使わず、「取得したページから項目を抜き出す」ことだけに使います。探索範囲はプログラムと `config/` のチェックリストで固定しているので、AI任せによる取りこぼしが起きにくく、起きても収集レポートで空欄として見えます。

### 信頼度のルール

| 表示 | 条件 |
|---|---|
| ● 確定 | 公式ソース1件以上、または独立した非ファンソース2件以上が一致 |
| ○ 未確認 | 上記を満たさない（ファンWikiだけ、1ソースだけ など） |
| ▲ 矛盾 | 公式・報道・百科のどれかに支持された別の値がある |

ルールは `config/sources.yaml` で変えられます。

## セットアップ

このアプリは inventory モノレポの `apps/idol-tracker/` にあります。ワークフローはリポジトリ直下の `.github/workflows/idol-tracker-*.yml` に置いてあり、いずれも `apps/idol-tracker` をカレントディレクトリとして動きます。

1. Pages は `idol-tracker pages` ワークフローが `apps/idol-tracker/docs` だけを配信します（モノレポではブランチ配信の `/docs` が使えないため、この方式です）。Source の設定はワークフローが自動で行うので、main にマージすれば数分で `https://<ユーザー名>.github.io/inventory/` が開けるようになります。うまくいかないときは **Settings → Pages** の Source が「GitHub Actions」になっているか確認してください。
2. **Settings → Secrets and variables → Actions** に登録します（収集を動かすときだけ必要。表示だけなら不要）。
   - `ANTHROPIC_API_KEY`（AI抽出用）
   - `BRAVE_API_KEY`（検索用。Google を使う場合は `GOOGLE_API_KEY` と `GOOGLE_CSE_ID` を登録し、Variables に `SEARCH_PROVIDER=google`）
3. **Settings → Actions → General** の一番下で「Allow GitHub Actions to create and approve pull requests」をオンにします。
4. **Actions → idol-tracker collect → Run workflow** を実行します。

検索APIとClaude APIの無料枠・料金は変わることがあるので、各サービスの公式ページで確認してください。費用の大部分はAI抽出なので、`max_pages_per_person`（1人あたりの取得ページ数）とモデル（Variables の `EXTRACT_MODEL`）で調整できます。

## 人物を追加する

Actions → idol-tracker collect → Run workflow に次を入れます。

| 入力 | 例 | メモ |
|---|---|---|
| name | 調べたい人の名前 | 検索に使う表記 |
| slug | 半角英数と_ | URLとファイル名に使うID。ハロプロなら公式プロフィールURLの末尾に合わせると、公式ページを検索に頼らず必ず取りに行きます |
| group | angerme | `apps/idol-tracker/config/groups.yaml` のキー |

別のグループの人を調べるときは、先に `config/groups.yaml` にグループと固定URL（`seed_urls`）を、`config/sources.yaml` にそのグループの公式ドメインを追加してください。

## 手で直す・足す

`manual/<slug>.yaml` に書いた内容は自動収集の結果と統合されます。自動収集の値より優先したいときは `pin: true` を付けます。

```yaml
sources:
  - { id: hp, url: "https://www.helloproject.com/...", title: "公式プロフィール" }
claims:
  - { kind: profile, field: birthplace, value: 千葉県, sources: [hp], pin: true }
  - { kind: event, type: stage, date: "2024-06-01", title: 舞台〇〇出演, sources: [hp] }
  - { kind: membership, group: angerme, role: 9期メンバー, date: "2020-11-02", sources: [hp] }
  - { kind: sns, service: Instagram, handle: "@example", url: "https://...", date: "2024-04-19", sources: [hp] }
```

誤った自動抽出を消したいときは、`claims/<slug>.jsonl` の該当行を削除して `python -m pipeline.build` を実行します。

## 取りこぼしを減らす工夫

- **チェックリスト**：`config/fields.yaml` に項目と検索クエリを定義。空欄は収集レポートに「未取得」と出ます。
- **固定URL**：公式プロフィールなど、検索順位に関係なく必ず読むページを `seed_urls` に指定。
- **公式優先の取得順**：公式 → 報道 → 百科 → その他 → ファン の順に本文を取得。
- **ページ分割**：長いページは重なりつきで分割して1片ずつ抽出。
- **2系統抽出**：正規表現でもプロフィール表を読むので、AIの見落としを補えます。
- **引用検証**：AIが返した引用が本文に無い事実は捨て、件数をレポートに記録。
- **再探索**：空欄と「未確認」の必須項目について、読み直しと追加検索を自動で実行。
- **SNS候補**：X や Instagram は本文を取らず、検索に出たURLを「SNS候補」としてレポートに出すだけにしています。

## 手元で動かす

リポジトリを clone したあと、`apps/idol-tracker` に移動して実行します。

```bash
cd apps/idol-tracker
pip install -r pipeline/requirements.txt
python -m unittest discover tests          # テスト
python -m pipeline.build                   # manual/ と claims/ から docs/data を再生成
export ANTHROPIC_API_KEY=... BRAVE_API_KEY=...
python -m pipeline.collect --slug rin_kawana --name 川名凜 --group angerme
python -m pipeline.collect --slug rin_kawana --name 川名凜 --no-ai   # AIを使わずルール抽出だけ
cd docs && python -m http.server          # http://localhost:8000 で表示確認
```

## フォルダ構成

`apps/idol-tracker/` の中身です。

```
config/     fields.yaml（チェックリスト）sources.yaml（ソース種別）groups.yaml（グループと固定URL）
pipeline/   search → fetch → extract → merge → coverage、collect.py が全体を実行
manual/     手入力データ（初期データの川名凜さん分を同梱）
claims/     自動抽出した事実（引用・出典URLつき。PRで差分を確認する対象）
docs/       GitHub Pages（HTML/CSS/JS と data/）
tests/      判定ロジックと収集フローのテスト
```

ワークフローだけはリポジトリ直下にあります。

```
.github/workflows/idol-tracker-collect.yml   収集して収集レポート付きPRを作る（手動・毎週月曜）
.github/workflows/idol-tracker-check.yml     テストと表示用データの再生成を確認
.github/workflows/idol-tracker-pages.yml     docs/ を GitHub Pages に配信
```

## 注意

- 同梱の川名凜さんの初期データは 2026年9月15日時点の検索結果から作ったものです。○未確認の項目は、公式発表で裏付けが取れるまで参考程度に見てください。
- 本文や画像は保存・公開せず、事実・短い引用・URLだけを残します。
- 公式に公表されていない個人的な情報は、収集対象や手入力に含めないでください。公開リポジトリの内容は誰でも見られます。
