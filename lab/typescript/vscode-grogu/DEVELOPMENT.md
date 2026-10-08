# 開発ガイド

Pixel Pal の開発・動作確認・公開のための文書です。使い方は [README.md](README.md) を参照してください。

フォルダ名は開発当初の名残で `vscode-grogu` のままです。拡張機能の名前は `pixel-pal` です。

## セットアップ

```sh
npm install
npm run compile
```

このフォルダを VS Code で直接開き、`F5` を押すと、拡張機能を読み込んだウィンドウが開きます。
モノレポのルートを開いた状態では `F5` の設定が効きません。

## 構成

拡張機能は、次の3つでできています。

| 部分 | 場所 | 役割 |
|------|------|------|
| 表示エンジン | `src/`、`media/` | 素材を読み込んでビュー内で動かす。エディタの出来事を検知して伝える |
| ペット素材 | `pets/mochi/` | 同梱のサンプルペット。ビルド済みの出力 |
| ペット作成スキル | `skills/pixel-pet-creator/` | AI 向けの手順書と、素材をビルドするツール |

表示エンジンは特定のキャラクターを知りません。フォルダの `manifest.json` に書かれたとおりに動かします。

| パス | 内容 |
|------|------|
| `src/extension.ts` | ビューの登録、設定、コマンド、スキルの導入 |
| `src/petLoader.ts` | ペットのフォルダの読み込みと検証（独自形式・Codex 形式） |
| `src/editorEvents.ts` | エディタ上の出来事の検知 |
| `media/main.js` | ビュー内でのペットの動きの制御 |
| `media/main.css` | レイアウトと CSS アニメーション |
| `media/icon.png` | 拡張機能のアイコン（生成物） |
| `skills/pixel-pet-creator/SKILL.md` | AI 向けの手順書 |
| `skills/pixel-pet-creator/scripts/` | 描画ライブラリとビルドコマンド（依存パッケージなし） |
| `skills/pixel-pet-creator/examples/mochi/pet.js` | サンプルペットの定義。`pets/mochi/` の元 |
| `scripts/make-icon.js` | アイコンの生成 |
| `scripts/make-demo-gif.js` | README 用のデモGIFの生成。画面録画ではなく、スプライトから合成した映像 |

### 処理の流れ

1. `extension.ts` が設定 `pixelPal.petPath` からフォルダを決め、`petLoader.ts` で読み込む。
2. 読み込んだ定義を WebView に渡し、`main.js` が歩行・待機・演技を制御する。
3. `editorEvents.ts` が出来事を検知し、WebView にメッセージで伝える。
4. ペットのフォルダを監視し、変更があれば自動で読み込み直す。

### スキルの届け方

| ツール | 方法 |
|------|------|
| GitHub Copilot（VS Code） | `package.json` の `contributes.chatSkills` で直接提供 |
| Claude Code / Cursor / Codex | `~/.claude/skills/` と `~/.agents/skills/` にコピー。初回起動時に確認を出す |

ホームフォルダに導入したスキルには版を記録し、拡張機能の更新時に入れ替えます。

## 素材の生成

```sh
npm run pet:sample   # サンプルペット（pets/mochi/）を作り直す
npm run icon         # アイコン（media/icon.png）を作り直す
npm run demo         # README 用のデモGIF（media/demo.gif）を作り直す
```

## ローカル専用のペット

`pets/` 配下は、`mochi` 以外をリポジトリ（`.gitignore`）からもパッケージ（`.vscodeignore`）からも除外しています。
既存の作品のキャラクターを描いたペットは、権利者の許諾がないファンアートにあたるため、公開物に含めません。

開発中のウィンドウでは、`pets/` にあるフォルダはすべて「同梱」として一覧に出ます。
配布物に入るかどうかは、次のコマンドで確認できます。

```sh
npx vsce ls --no-dependencies
```

## 動作確認

`F5` で開いたウィンドウで確認します。確認用のフォルダを開いておくと、保存やエラーを試せます。

| 項目 | 手順 | 期待する結果 |
|------|------|------|
| 表示 | エクスプローラーの「Pixel Pal」を展開 | サンプルペットが歩いたり立ち止まったりする |
| クリック | パネル、ペットをクリック | その位置へ歩く、反応する |
| シークレット | ペットを2秒以内に5回クリック | シークレット演出が出る |
| 切り替え | 「ペットを選ぶ」で別のペットを選ぶ | 切り替わり、タイトル横に名前が出る |
| 個別再生 | 「アニメーションを再生（確認用）」 | 選んだアニメーションが再生される |
| 保存 | ファイルを保存 | `save` に対応する演技（確率つきの場合あり） |
| エラー | 構文エラーを書いて2秒待つ、直して2秒待つ | `error`、`errorsCleared` に対応する演技 |
| コマンド | ターミナルで失敗・成功するコマンドを実行 | `fail`、`success` に対応する演技 |
| 離席 | `pixelPal.awayMinutes` を `0.5` にして待つ | `away` に対応する演技。操作すると中断 |
| 自動反映 | 表示中のペットをビルドし直す | ビューが自動で読み込み直される |
| スキル導入 | 「ペット作成スキルをAIツールに導入」 | 選んだ場所にスキルが配置される |

同じきっかけには6秒間は続けて反応しません。

うまく動かないときは、次を確認します。

- ビューの中: コマンド「開発者: Webview 開発者ツールを開く」の Console
- 拡張機能本体: 元のウィンドウの「デバッグ コンソール」

## パッケージ化

```sh
npm run package
```

`pixel-pal-<版>.vsix` ができます。次のコマンドで、マーケットプレイスを通さずにインストールできます。

```sh
code --install-extension pixel-pal-0.0.1.vsix
```

モノレポのサブフォルダにあるため、README 内の相対リンクが正しく解決されるよう、
`package` スクリプトでリンクの基準 URL を指定しています。リポジトリやブランチを変えたら、ここも直します。

## マーケットプレイスへの公開

マーケットプレイスに限定公開の仕組みはありません。公開すると、誰でもインストールできます。

### 公開前の作業

- [x] 発行者 ID を作り、`package.json` の `publisher` に設定する（`p-ota-q`）
- [x] ライセンスを決め、`LICENSE` を追加して `package.json` に `license` を書く（MIT）
- [x] `package.json` の `private` を削除する
- [x] ストア用の GIF を用意し、README に載せる（`media/demo.gif`）
- [ ] ブランチを `main` にマージする（README の画像とリンクは `main` を参照するため、マージ前は表示されない）
- [ ] 下記「未検証の点」を実機で確認する

### 公開の手順

```sh
npx vsce login <発行者ID>
npx vsce publish --no-dependencies --baseContentUrl <基準URL> --baseImagesUrl <基準URL>
```

Cursor などの VS Code 派生エディタは Open VSX を参照します。そちらにも公開する場合は `ovsx` を使います。

### 規約上の注意

- 自作、または許諾のあるものだけを載せる。既存キャラクターの素材を同梱しない。
- 他社の製品名（Codex、Claude Code、Cursor、GitHub Copilot）は、対応先の説明にだけ使う。表示名やアイコンには使わない。
- ホームフォルダへの書き込み（スキルの導入）は、利用者の確認を経て行い、README に明記する。

## 未検証の点

- スキルを使って、AI に新しいペットを一から作らせる通しの試験
- 実際の Codex 製ペットでの表示（仕様書に基づく実装）
- GitHub Copilot へのスキルの直接提供、Cursor・Codex でのスキルの読み込み（各ツールの公開情報に基づく実装）

## 制約

- ペットが見えるのは、ビューが表示されている間だけ。VS Code の画面全体に重ねる公式の方法は無い。
- 表示できるペットは1体。
- 画面の表示は日本語のみ。
