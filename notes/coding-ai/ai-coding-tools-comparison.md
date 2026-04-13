# AIコーディングツール比較

> 最終更新: 2026年4月14日  
> 公式ドキュメントをもとに調査。料金・機能は変更される可能性があります。

## ツール一覧

| ツール | 開発元 | 形態 | 公式URL |
|--------|--------|------|---------|
| GitHub Copilot | GitHub (Microsoft) | IDE拡張 + クラウドエージェント + CLI | https://github.com/features/copilot |
| Cursor | Anysphere | スタンドアロンIDE（VS Code fork） | https://www.cursor.com/ |
| Claude Code | Anthropic | CLI + IDE拡張 + デスクトップアプリ + Web | https://code.claude.com/ |
| Antigravity | 不明 | 不明 | https://antigravity.dev/ （※現在アクセス不可） |

---

## 1. GitHub Copilot

**公式ドキュメント**: https://docs.github.com/copilot

### 概要
GitHub/Microsoft が提供するAIコーディングアシスタント。VS Code、Visual Studio、JetBrains、Xcode、Neovim、Eclipse など幅広いIDEに対応。GitHub上でのコードレビューやIssueのトリアージまで統合されている。

### 主な機能
- **インライン補完**: コード入力中のリアルタイム補完（コンテキスト対応）
- **エージェントモード**: チャットで指示し、ファイル編集・コマンド実行を自律的に実行
- **クラウドエージェント**: GitHub Issue を割り当てると、バックグラウンドでPRを自動作成
- **コードレビュー**: PR上でのAIレビュー
- **Copilot CLI**: ターミナル上での自然言語コマンド
- **カスタマイズ**: `AGENTS.md`、`.instructions.md`、カスタムエージェント、MCP対応
- **マルチモデル**: Anthropic、Google、OpenAI のモデルから選択可能

### 対応プラットフォーム
VS Code, Visual Studio, JetBrains IDEs, Xcode, Neovim, Eclipse, Raycast, SQL Server Management Studio, Zed

### 料金プラン（個人向け）

| プラン | 月額 | 主な内容 |
|--------|------|----------|
| Free | $0 | 50エージェントリクエスト/月、2,000補完/月 |
| Pro | $10 | 無制限補完、300プレミアムリクエスト、クラウドエージェント、コードレビュー |
| Pro+ | $39 | 1,500プレミアムリクエスト、全モデルアクセス、GitHub Spark |

**料金詳細**: https://github.com/features/copilot/plans

---

## 2. Cursor

**公式ドキュメント**: https://cursor.com/docs

### 概要
Anysphere社が開発したAI特化型コードエディタ。VS Code をフォークしており、VS Code の拡張機能がそのまま使える。独自のTabモデルによる高精度な自動補完と、エージェント型開発が特徴。

### 主な機能
- **Tab補完**: 独自に特化したモデルにより、次のアクションを高速・高精度で予測
- **エージェントモード**: タスクを指示すると、コードベースを理解した上でファイル編集を自律実行
- **クラウドエージェント**: 専用コンピューターで機能構築・テスト・デモをエンドツーエンドで実行
- **コードベースインデックス**: プロジェクト全体をセマンティックに理解
- **Bugbot**: PR上でのAIコードレビュー
- **マルチモデル**: OpenAI、Anthropic、Google、xAI、Cursor独自モデルから選択
- **MCP対応**: スキル、フック、MCPサーバー連携
- **CLI**: ターミナルからCursorを起動・操作

### 対応プラットフォーム
Windows, macOS, Linux（スタンドアロンエディタ）
GitHub, Slackとの連携あり

### 料金プラン（個人向け）

| プラン | 月額 | 主な内容 |
|--------|------|----------|
| Hobby | 無料 | エージェントリクエスト制限あり、Tab補完制限あり |
| Pro | $20 | エージェント上限拡張、最先端モデル、MCP/スキル/フック、クラウドエージェント |
| Pro+ | $60 | 全モデルで3倍の使用量 |
| Ultra | $200 | 全モデルで20倍の使用量、新機能優先アクセス |

**料金詳細**: https://www.cursor.com/pricing

---

## 3. Claude Code

**公式ドキュメント**: https://code.claude.com/docs/en/overview

### 概要
Anthropic社が開発したエージェント型コーディングツール。ターミナルCLIが中心で、VS Code・JetBrains・デスクトップアプリ・Webブラウザからも利用可能。コードベース全体を読み取り、ファイル編集やコマンド実行を自律的に行う。

### 主な機能
- **エージェント型開発**: コードベースを理解した上で、機能構築・バグ修正・リファクタリングを自律実行
- **コードオンボーディング**: 数秒でコードベース全体をマッピング・説明
- **Issue → PR**: GitHub/GitLabのIssueを読み取り、コードを書き、テスト実行、PR提出まで自動化
- **マルチファイル編集**: 依存関係を理解した上での複数ファイル同時編集
- **CLAUDE.md**: プロジェクト固有の指示を永続化
- **MCP対応**: 外部ツール・API連携
- **カスタムエージェント**: Agent SDKで独自ワークフローを構築可能
- **マルチプラットフォーム**: ターミナル、VS Code、JetBrains、デスクトップ、Web、Slack
- **スケジュールタスク**: 定期実行タスクの設定

### 対応プラットフォーム
ターミナルCLI（Windows/macOS/Linux）、VS Code、JetBrains、デスクトップアプリ、Web、iOS、Slack、Chrome

### 料金プラン（個人向け）

| プラン | 月額 | 主な内容 |
|--------|------|----------|
| Pro | $20（年払い $17） | Sonnet 4.6 + Opus 4.6、短時間コーディング向け |
| Max 5x | $100 | 日常的な利用に最適、大規模コードベース対応 |
| Max 20x | $200 | パワーユーザー向け、最大量のアクセス |

**料金詳細**: https://claude.com/pricing

---

## 4. Antigravity

**公式サイト**: https://antigravity.dev/

> ⚠️ 2026年4月14日現在、公式サイトにアクセスできない状態です。情報が確認でき次第追記します。

---

## 比較表

| 項目 | GitHub Copilot | Cursor | Claude Code |
|------|---------------|--------|-------------|
| **形態** | IDE拡張 | スタンドアロンIDE | CLI + IDE拡張 |
| **ベースエディタ** | VS Code等に統合 | VS Code fork | 独自CLI / VS Code拡張 |
| **インライン補完** | ✅ | ✅（独自Tabモデル） | ❌ |
| **エージェントモード** | ✅ | ✅ | ✅（デフォルト動作） |
| **クラウドエージェント** | ✅ | ✅ | ✅（Web/デスクトップ） |
| **コードレビュー** | ✅ | ✅（Bugbot） | ✅（GitHub Actions連携） |
| **CLI** | ✅ | ✅ | ✅（メイン機能） |
| **マルチモデル** | ✅ | ✅ | ❌（Claudeのみ） |
| **MCP対応** | ✅ | ✅ | ✅ |
| **カスタム指示** | AGENTS.md / .instructions.md | .cursor/rules | CLAUDE.md |
| **無料プラン** | あり（50リクエスト/月） | あり（制限付き） | なし |
| **有料最安** | $10/月 | $20/月 | $20/月 |
| **開発元** | GitHub (Microsoft) | Anysphere | Anthropic |

## 選び方の目安

- **既存のIDE環境を変えたくない** → GitHub Copilot（幅広いIDE対応）
- **AI特化のエディタ体験が欲しい** → Cursor（Tab補完が強力）
- **ターミナル中心のワークフロー / 大規模リファクタリング** → Claude Code（CLIベース）
- **コストを抑えたい** → GitHub Copilot Free or Pro（$0〜$10/月）
- **複数AIモデルを使い分けたい** → GitHub Copilot or Cursor
