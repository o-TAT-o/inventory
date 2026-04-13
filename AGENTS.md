# Project Guidelines

## 一般

- コード生成やコメント、ドキュメントは日本語で記述してください。
- このリポジトリは個人開発用のモノレポです。

## リポジトリ構造

- `apps/` — 公開レベルの完成度を持つアプリ（就活・ポートフォリオ等）
- `lab/` — 技術検証・実験コード（typescript / python / go）
- `learning/` — 資格学習（Azure）・英語学習用のコード・メモ
- `articles/` — ZennやQiitaなどの技術記事原稿
- `notes/` — 特定のアプリに依存しない汎用的な技術調査メモ
- `scripts/` — リポジトリ運用の自動化スクリプト

## コーディングスタイル

### TypeScript
- インデント: スペース2つ
- セミコロン: あり
- クォート: シングルクォート
- 型定義を積極的に使用する

### Python
- PEP 8 に準拠
- インデント: スペース4つ
- 型ヒントを使用する

### Go
- `gofmt` に準拠
- インデント: タブ

## コミットメッセージ

[Conventional Commits](https://www.conventionalcommits.org/) に従ってください。

> **要約**: コミットメッセージに `type(scope): 説明` の形式を使い、変更内容を人間にもツールにも分かりやすくする規約。  
> SemVer と連動し、`fix` → PATCH、`feat` → MINOR、`BREAKING CHANGE` → MAJOR に対応する。  
> CHANGELOG の自動生成やバージョンバンプの自動化に活用できる。

### フォーマット

```
<type>(<scope>): <subject>

[任意の本文]

[任意のフッター]
```

### type 一覧

| type | 意味 | 例 |
|------|------|-----|
| `feat` | 新機能 | `feat(lab/typescript): Zodバリデーションのサンプルを追加` |
| `fix` | バグ修正 | `fix(apps): ログインページのリダイレクトループを修正` |
| `docs` | ドキュメントのみ | `docs: READMEにディレクトリ構成図を追加` |
| `style` | フォーマット変更（動作に影響なし） | `style(lab/python): Black でフォーマット適用` |
| `refactor` | リファクタリング | `refactor(apps): API クライアントを共通モジュールに分離` |
| `test` | テスト追加・修正 | `test(lab/go): ハンドラーのユニットテストを追加` |
| `chore` | ビルド・ツール変更 | `chore: .editorconfig を追加` |

### scope の付け方

`scope` にはディレクトリ名を指定してください（例: `feat(lab/typescript): ...`）。  
リポジトリ全体に関わる変更の場合は scope を省略してもOKです（例: `docs: READMEを更新`）。

### 破壊的変更

破壊的変更がある場合は、type の後に `!` を付けるか、フッターに `BREAKING CHANGE:` を記述してください。

```
feat(apps)!: 認証方式をJWTからOAuth2に変更
```
