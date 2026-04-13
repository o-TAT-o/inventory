# Google Gemini モデル比較

> 最終更新: 2026年4月14日  
> 公式ドキュメント: https://ai.google.dev/gemini-api/docs/models

## モデルファミリー概要

Google が開発する Gemini ファミリー。テキスト、画像、動画、音声などマルチモーダル対応が強み。GitHub Copilot では Gemini 2.5 Pro、Gemini 3 Flash、Gemini 3.1 Pro が利用可能。

## GitHub Copilot で利用可能なモデル

| モデル | ステータス | 倍率（Chat） | 特徴 |
|--------|------------|-------------|------|
| Gemini 2.5 Pro | GA | 1x | 高度な推論・コーディング |
| Gemini 3 Flash | Public preview | 0.33x | 低コスト、大規模モデル並みの性能 |
| Gemini 3.1 Pro | Public preview | 1x | 最新のフラッグシップ |

## 各モデルの特徴

### Gemini 3.1 Pro

公式の説明: *「高度なインテリジェンス、複雑な問題解決能力、強力なエージェント機能とバイブコーディング機能」*

- Gemini 3 世代の最新フラッグシップモデル
- 複雑な問題解決、エージェント型ワークフロー、高度なコーディングに最適
- Gemini 3 Pro の後継（3 Pro は 2026年3月に非推奨・シャットダウン済み）

### Gemini 3 Flash

公式の説明: *「わずかな費用で大規模なモデルに匹敵するフロンティアクラスのパフォーマンス」*

- 高速処理と低コストが特徴
- GitHub Copilot での倍率が 0.33x と非常にコスト効率が高い
- 日常的なタスクや大量処理に最適

### Gemini 2.5 Pro

公式の説明: *「高度な推論機能とコーディング機能を備えた、複雑なタスク向けの最先端モデル」*

- Gemini 2.5 世代のフラッグシップ
- 推論を必要とするタスクに強い
- GA（一般公開）済みで安定性が高い

## Gemini ファミリーの全体像（GitHub Copilot 対象外含む）

| カテゴリ | モデル | 特徴 |
|----------|--------|------|
| テキスト/推論 | Gemini 3.1 Pro | 最新フラッグシップ |
| テキスト/推論 | Gemini 3 Flash | 高速・低コスト |
| テキスト/推論 | Gemini 2.5 Pro | 安定した推論モデル |
| テキスト/推論 | Gemini 2.5 Flash | 推論対応の高速モデル |
| テキスト/推論 | Gemini 2.5 Flash-Lite | 最速・最もコスト効率が高い |
| 画像生成 | Nano Banana / Nano Banana 2 | ネイティブ画像生成・編集 |
| 動画生成 | Veo 3.1 | 映画のような動画生成 |
| 音声 | Gemini TTS系 | テキスト読み上げ |
| エージェント | コンピュータ使用 | UI操作の自動化 |
| 検索 | Deep Research | 自律的な多段階リサーチ |

## GitHub Copilot での利用可能プラン

| モデル | Free | Pro ($10) | Pro+ ($39) |
|--------|------|-----------|------------|
| Gemini 2.5 Pro | ❌ | ✅ | ✅ |
| Gemini 3 Flash | ❌ | ✅ | ✅ |
| Gemini 3.1 Pro | ❌ | ✅ | ✅ |

## その他 GitHub Copilot で利用可能なモデル

### Grok Code Fast 1（xAI）

| 項目 | 値 |
|------|-----|
| 開発元 | xAI |
| 倍率 | 0.25x |
| ステータス | GA |
| プラン | Free含む全プラン |

xAI が開発するGrokファミリーのコーディング特化モデル。0.25xと非常に低コストで利用可能。

## 参考リンク

- Gemini API モデル一覧: https://ai.google.dev/gemini-api/docs/models
- Gemini API 料金: https://ai.google.dev/gemini-api/docs/pricing
- GitHub Copilot 対応モデル: https://docs.github.com/en/copilot/reference/ai-models/supported-models
