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

```
<type>(<scope>): <subject>
```

- `feat`: 新機能
- `fix`: バグ修正
- `docs`: ドキュメントのみの変更
- `style`: コードの意味に影響しない変更（空白、フォーマット等）
- `refactor`: バグ修正でも新機能でもないコード変更
- `test`: テストの追加・修正
- `chore`: ビルドプロセスや補助ツールの変更

`scope` にはディレクトリ名を指定してください（例: `feat(lab/typescript): ...`）。
