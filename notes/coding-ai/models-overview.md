# GitHub Copilot 対応 AI モデル一覧

> 最終更新: 2026年4月14日  
> 公式ドキュメント: https://docs.github.com/en/copilot/using-github-copilot/ai-models/supported-ai-models-in-copilot

## モデル一覧サマリー

GitHub Copilot で利用可能な全モデルの横断比較。倍率はチャットでのプレミアムリクエスト消費量を示す（0x = 無料、0.25x〜0.33x = 低コスト、1x = 標準、3x+ = 高コスト）。

## 全モデル比較表

| モデル | 開発元 | 倍率 | ステータス | Free | Pro | Pro+ |
|--------|--------|------|----------|------|-----|------|
| **Claude Haiku 4.5** | Anthropic | 0.33x | GA | ✅ | ✅ | ✅ |
| **Claude Sonnet 4** | Anthropic | 1x | GA | ❌ | ✅ | ✅ |
| **Claude Sonnet 4.5** | Anthropic | 1x | GA | ❌ | ✅ | ✅ |
| **Claude Sonnet 4.6** | Anthropic | 1x | GA | ❌ | ✅ | ✅ |
| **Claude Opus 4.5** | Anthropic | 3x | GA | ❌ | ✅ | ✅ |
| **Claude Opus 4.6** | Anthropic | 3x | GA | ❌ | ✅ | ✅ |
| **Claude Opus 4.6 (fast)** | Anthropic | 30x | Preview | ❌ | ❌ | ✅ |
| **GPT-4.1** | OpenAI | 0x | GA | ✅ | ✅ | ✅ |
| **GPT-5 mini** | OpenAI | 0x | GA | ✅ | ✅ | ✅ |
| **GPT-5.2** | OpenAI | 1x | GA | ❌ | ✅ | ✅ |
| **GPT-5.2-Codex** | OpenAI | 1x | GA | ❌ | ✅ | ✅ |
| **GPT-5.3-Codex** | OpenAI | 1x | GA | ❌ | ✅ | ✅ |
| **GPT-5.4** | OpenAI | 1x | GA | ❌ | ✅ | ✅ |
| **GPT-5.4 mini** | OpenAI | 0.33x | GA | ❌ | ✅ | ✅ |
| **Gemini 2.5 Pro** | Google | 1x | GA | ❌ | ✅ | ✅ |
| **Gemini 3 Flash** | Google | 0.33x | Preview | ❌ | ✅ | ✅ |
| **Gemini 3.1 Pro** | Google | 1x | Preview | ❌ | ✅ | ✅ |
| **Grok Code Fast 1** | xAI | 0.25x | GA | ✅ | ✅ | ✅ |
| **Raptor mini** | GitHub | 0x | Preview | ✅ | ✅ | ✅ |
| **Goldeneye** | GitHub | — | Preview | ✅ | ❌ | ❌ |

## コスト効率ランキング

プレミアムリクエスト消費が少ない順（コスト効率が高い順）:

| 倍率 | モデル |
|------|--------|
| **0x（無料）** | GPT-4.1, GPT-5 mini, Raptor mini |
| **0.25x** | Grok Code Fast 1 |
| **0.33x** | Claude Haiku 4.5, GPT-5.4 mini, Gemini 3 Flash |
| **1x** | Claude Sonnet 4/4.5/4.6, GPT-5.2, GPT-5.2/5.3-Codex, GPT-5.4, Gemini 2.5 Pro, Gemini 3.1 Pro |
| **3x** | Claude Opus 4.5, Claude Opus 4.6 |
| **30x** | Claude Opus 4.6 (fast mode) |

## プランごとの月額と含まれるプレミアムリクエスト

| プラン | 月額 | プレミアムリクエスト | 補足 |
|--------|------|---------------------|------|
| Free | $0 | チャット・エージェント 50回/月、補完 2,000回/月 | Haiku 4.5, GPT-5 mini 等の無料モデルのみ |
| Pro | $10 | 300回/月（追加購入可） | GPT-5 mini でのチャット・エージェントは無制限、インライン補完も無制限 |
| Pro+ | $39 | 1,500回/月（Proの5倍、追加購入可） | GPT-5.4, Opus 4.6 fast 等の上位モデルにもアクセス可 |

> **注**: プレミアムリクエストは倍率で重み付きカウントされる。例: Opus 4.6（3x）を1回使用 = 3回分消費。

## 用途別おすすめモデル

### コスト重視（無料/低消費）
- **GPT-4.1**: 無料で基本的なコーディング支援
- **GPT-5 mini**: 無料でGPT-5の高速応答
- **Grok Code Fast 1**: 0.25xでコーディング特化

### バランス重視（日常利用）
- **Claude Sonnet 4.6**: 1xで高い知能と速度のバランス
- **GPT-5.3-Codex**: 1xでコーディング特化の最新モデル
- **Gemini 2.5 Pro**: 1xで安定した推論能力

### 品質重視（複雑なタスク）
- **Claude Opus 4.6**: 3xだが最高水準の推論能力
- **GPT-5.4**: 1xで最新のフラッグシップ（Pro以上）
- **Gemini 3.1 Pro**: 1xでGoogle最新のエージェント対応モデル

## 各社モデルの詳細ドキュメント

- [Anthropic Claude モデル比較](models-anthropic-claude.md)
- [OpenAI GPT モデル比較](models-openai-gpt.md)
- [Google Gemini モデル比較](models-google-gemini.md)

## 参考リンク

- GitHub Copilot 対応モデル一覧: https://docs.github.com/en/copilot/using-github-copilot/ai-models/supported-ai-models-in-copilot
- GitHub Copilot 料金プラン: https://github.com/features/copilot/plans
