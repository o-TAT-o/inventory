# inventory

個人開発用のモノレポリポジトリです。  
アプリ開発、技術検証、資格学習、記事執筆、技術メモなどを一元管理します。

## ディレクトリ構成

```
inventory/
├── apps/          # 公開用アプリ（就活・ポートフォリオ等）
├── lab/           # 技術検証・実験
│   ├── typescript/
│   │   └── docs/    # 関連ドキュメント
│   ├── python/
│   └── go/
├── learning/      # 資格・言語学習
│   ├── azure/
│   └── english/
├── articles/      # ブログ・記事原稿
├── notes/         # 技術調査メモ
└── scripts/       # 自動化スクリプト
```

| ディレクトリ | 用途 |
|-------------|------|
| `apps/` | 公開レベルの完成度を持つアプリ（就活用アプリやポートフォリオなど） |
| `lab/` | TypeScript・Python・Goなどの技術検証・実験コード |
| `learning/` | Azure資格（AZ-104, AZ-204等）や英語学習用のコード・メモ |
| `articles/` | ZennやQiitaなどの技術記事の原稿 |
| `notes/` | 特定のアプリに依存しない汎用的な技術調査メモ |
| `scripts/` | リポジトリ運用のための自動化スクリプト |

## セットアップ

### GitHub Copilot（モノレポ対応）

サブディレクトリを単独で開いた場合でも、リポジトリルートのカスタマイズファイル（`AGENTS.md` 等）を検出させるには、以下の VS Code 設定を有効にしてください。

```jsonc
// settings.json
{
  "chat.useCustomizationsInParentRepositories": true
}
```

### コミットテンプレート

リポジトリには [Conventional Commits](https://www.conventionalcommits.org/) 形式のコミットテンプレート（`.gitmessage`）が含まれています。  
クローン後に以下を実行してテンプレートを有効化してください。

```sh
git config --local commit.template .gitmessage
```
