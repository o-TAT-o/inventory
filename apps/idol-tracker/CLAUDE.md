# 引き継ぎメモ（Claude Code 向け）

claude.ai のチャットで設計・実装したプロジェクト。経緯と決定事項をここにまとめる。
使い方と仕組みの詳細は README.md を参照。

## 配置
- inventory モノレポの `apps/idol-tracker/` に置いている。リポジトリ全体の規約は直下の `AGENTS.md`（日本語で書く、Conventional Commits、scope はディレクトリ名）に従う
- パイプラインのパスはこのフォルダ基準なので、コマンドは `apps/idol-tracker` をカレントディレクトリにして実行する
- ワークフローはリポジトリ直下の `.github/workflows/idol-tracker-{collect,check,pages}.yml`。いずれも `working-directory: apps/idol-tracker` で動く
- Pages はモノレポのためブランチ配信の `/docs` が使えず、`idol-tracker-pages.yml` で `docs/` をアーティファクト配信する（Source の有効化は configure-pages の enablement で自動）

## 目的
- アンジュルムの川名凜さんから始めて、他メンバー・他グループにも広げられる情報収集アプリ
- 取得したい情報: 加入時期、加入前の活動、研修生・ジュニア時代、SNS、生年月日・出身地など
- 年代と活動記録は表・年表で見やすく。文章は少なく、Wiki のようにはしない
- ネット上の情報を網羅的に探す。AI任せによる取得漏れを防ぐ

## 決定事項
- ホスティング: GitHub Pages（docs/ を配信）＋ GitHub Actions（収集バッチ）。サーバーなし
- AIの役割は「取得済みページからの抽出」だけ。探索範囲は config/ のチェックリストとコードで固定
- 取得漏れ対策: 項目×クエリテンプレート、seed_urls、公式優先の取得順、ページ分割、
  正規表現とAIの2系統抽出、引用の実在チェック、空欄・未確認の必須項目の再探索（最大2ラウンド）
- 信頼度: 公式1件 or 独立した非ファンソース2件で「確定」、それ以外「未確認」、非ファンに支持された別値があれば「矛盾」
- 収集結果は自動マージせず、レポート付きPRで人が確認してからマージ
- X/Instagram/TikTok/YouTube などは本文を取得しない（URL候補としてレポートに出すだけ）
- 本文・画像は保存・公開しない。事実、短い引用、URLのみ
- フロントはビルド不要の素の HTML/CSS/JS（ES modules）
- 検索API: Brave（既定）または Google Programmable Search。AI: Claude API（EXTRACT_MODEL で変更可）

## 現在の状態
- 実装済み: docs/（人物・グループ・一覧画面）、pipeline/、config/、Actions（collect / check / pages）、テスト16件
- 初期データ: manual/rin_kawana.yaml（2026-09-15 のWeb検索で確認。期・事務所・習志野市PR大使などは未確認）
- 未検証: 実際の検索API・Claude API・ページ取得との接続（作成環境がネット不可だったため、モックでのみテスト）

## 次にやること
1. Pages（Source: GitHub Actions）、Secrets（ANTHROPIC_API_KEY, BRAVE_API_KEY）、Actions のPR作成許可を設定
2. idol-tracker collect を川名凜さん（slug: rin_kawana, group: angerme）で実行し、PRのレポートと claims/ を確認
3. 取得漏れ・誤抽出があれば config/fields.yaml のクエリ、extract.py のルール、prompts/extract.md を調整
4. 他メンバーを追加。他グループは config/groups.yaml と config/sources.yaml に先に追加

## 開発メモ
- コマンドはすべて `apps/idol-tracker` で実行する
- テスト: `python -m unittest discover tests`
- 表示データ再生成: `python -m pipeline.build`
- ローカル表示: `cd docs && python -m http.server`
- ユーザーはスマホ中心。文章は日本語で、短く
