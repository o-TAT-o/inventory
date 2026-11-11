# notes

汎用的な技術調査メモを管理するディレクトリです。  
特定のアプリに依存しない技術調査や学習メモを配置します。

## ディレクトリ構成

```
notes/
├── coding-ai/          # AI コーディングツール・モデル関連
├── dnp/                # 生成 AI の活用事例
├── prompt/             # AI に渡すプロンプトの下書き
└── windows/            # Windows の設定・運用メモ
```

## ドキュメント一覧

### coding-ai/ — AI コーディングツール・モデル

| ドキュメント | 概要 |
|-------------|------|
| [models-overview.md](coding-ai/models-overview.md) | GitHub Copilot 対応 AI モデル一覧（全モデル横断比較） |
| [models-anthropic-claude.md](coding-ai/models-anthropic-claude.md) | Anthropic Claude モデル比較（Opus / Sonnet / Haiku） |
| [models-openai-gpt.md](coding-ai/models-openai-gpt.md) | OpenAI GPT モデル比較（GPT-4.1 〜 GPT-5.4） |
| [models-google-gemini.md](coding-ai/models-google-gemini.md) | Google Gemini モデル比較（2.5 Pro / 3 Flash / 3.1 Pro） |
| [ai-coding-tools-comparison.md](coding-ai/ai-coding-tools-comparison.md) | AI コーディングツール比較（Copilot / Cursor / Claude Code） |
| [claude-code-meeting-questions.md](coding-ai/claude-code-meeting-questions.md) | Claude Code 導入検討 — AWS営業打ち合わせ質問集 |
| [github-copilot-harness.md](coding-ai/github-copilot-harness.md) | VS Code の Copilot ハーネス調査（Local と Copilot の違い・組み込み GitHub MCP・セキュリティ） |

### dnp/ — 生成 AI の活用事例

| ドキュメント | 概要 |
|-------------|------|
| [生成AIユースケース.md](dnp/生成AIユースケース.md) | GitHub Copilot Agent による Dependabot 対応の自動化の記録 |

### prompt/ — プロンプトの下書き

| ドキュメント | 概要 |
|-------------|------|
| [AppService_アクセストークン.md](prompt/AppService_アクセストークン.md) | App Service（Easy Auth）のトークンを API Management で検証する Vue アプリを、`lab/` に作らせるプロンプト |
| [デザインシステム.md](prompt/デザインシステム.md) | （未記入） |

### windows/ — Windows の設定・運用

| ドキュメント | 概要 |
|-------------|------|
| [keyboard-layout-per-device.md](windows/keyboard-layout-per-device.md) | 内蔵キーボードと外付けキーボードで配列（JIS / US）を分ける方法 |
| [remote-access-with-tailscale.md](windows/remote-access-with-tailscale.md) | 据え置き PC にスマホ・ノート PC から接続する（Tailscale・電源設定・Pro + RDP・Remote Control の常駐と通知・方式の比較） |
