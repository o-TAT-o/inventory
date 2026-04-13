# OpenAI GPT モデル比較

> 最終更新: 2026年4月14日  
> 公式ドキュメント: https://openai.com/index/introducing-gpt-5/

## モデルファミリー概要

OpenAI が開発する GPT ファミリー。GitHub Copilot では GPT-4.1 から GPT-5.4 まで複数のモデルが利用可能。GPT-5 は2つのモデル（高速応答用 + 複雑問題用）とルーター機能からなる統合システム。

## GitHub Copilot で利用可能なモデル

| モデル | ステータス | 倍率（Chat） | 特徴 |
|--------|------------|-------------|------|
| GPT-4.1 | GA | 0x（無料） | 軽量、基本的なタスク向け |
| GPT-5 mini | GA | 0x（無料） | GPT-5 の小型高速版 |
| GPT-5.2 | GA | 1x | GPT-5 系列の安定版 |
| GPT-5.2-Codex | GA | 1x | コーディング特化 |
| GPT-5.3-Codex | GA | 1x | コーディング特化（最新） |
| GPT-5.4 | GA | 1x | 最新のフラッグシップ |
| GPT-5.4 mini | GA | 0.33x | 5.4の小型版、コスト効率良好 |

**注**: GPT-5.1 は 2026年4月15日にリタイア予定。後継は GPT-5.3-Codex。

## GPT-5 の特徴（公式発表に基づく）

### 統合システム
GPT-5 は単一モデルではなく、以下の2つのモデルとルーターで構成される：
1. **高速応答用モデル（高スループット）** — 幅広い質問に高速・効率的に回答
2. **複雑問題用モデル（深い推論 / GPT-5 Thinking）** — 複雑な問題に対して深い推論を実行
3. **リアルタイムルーター** — 質問の種類に応じて最適なモデルを自動選択

### コーディング性能
- SWE-bench: 74.9%
- Aider-Polyglot: 88%
- 複雑なフロントエンド生成、大規模リポジトリのデバッグに強み
- 1つのプロンプトからレスポンシブなWebサイト・アプリ・ゲームを生成可能

### 信頼性の向上
- ハルシネーション（事実誤認）が GPT-4o 比で約45%低下（Web検索有効時）
- Thinking モード使用時は o3 比で約80%低下
- 実行不可能なタスクを正確に見極め、限界を明確に伝える能力が向上
- 迎合的な回答が 14.5% → 6%未満に改善

### 効率性
- GPT-5 Thinking は o3 と比較して出力トークンを50〜80%削減しつつ、より高い性能

## Codex モデルについて

GPT-5.2-Codex、GPT-5.3-Codex はコーディングタスクに特化したファインチューニング版。GitHub Copilot のエージェントモードやクラウドエージェントでの利用に最適化されている。

## Raptor mini / Goldeneye

| モデル | ベース | 倍率 | 特徴 |
|--------|--------|------|------|
| Raptor mini | ファインチューンド GPT-5 mini | 0x（無料） | GitHub が独自にファインチューンした軽量モデル（Public preview） |
| Goldeneye | ファインチューンド GPT-5.1-Codex | 補完のみ | インライン補完専用、Freeプランのみ（Public preview） |

## GitHub Copilot での利用可能プラン

| モデル | Free | Pro ($10) | Pro+ ($39) |
|--------|------|-----------|------------|
| GPT-4.1 | ✅ | ✅ | ✅ |
| GPT-5 mini | ✅ | ✅ | ✅ |
| GPT-5.2 | ❌ | ✅ | ✅ |
| GPT-5.2-Codex | ❌ | ✅ | ✅ |
| GPT-5.3-Codex | ❌ | ✅ | ✅ |
| GPT-5.4 | ❌ | ✅ | ✅ |
| GPT-5.4 mini | ❌ | ✅ | ✅ |
| Raptor mini | ✅ | ✅ | ✅ |

## 参考リンク

- GPT-5 発表: https://openai.com/index/introducing-gpt-5/
- OpenAI API モデル一覧: https://platform.openai.com/docs/models
- GitHub Copilot 対応モデル: https://docs.github.com/en/copilot/reference/ai-models/supported-models
