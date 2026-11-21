# site

GitHub Pages（`https://p-ota-q.github.io/inventory/`）に配信するサイトの設定です。
モノレポの中のいくつかのフォルダを集めて1つのサイトにし、ホームと共通ナビを付けます。

| ファイル | 役割 |
|---|---|
| `pages.json` | 公開するページの一覧。ここに書いたフォルダだけが配信される |
| `home.html` | ホーム（サイトのルート）のテンプレート |
| `site-nav.js` | 全ページの上に出る共通ナビ（Web Component。ページ側の CSS と干渉しない） |
| `build.py` | 上の3つから `_site/` を組み立てる。`.github/workflows/pages.yml` が実行する |

元のファイルは書き換えません。共通ナビは、配信用にコピーした HTML にだけ差し込みます。

## ページを追加する

1. `pages.json` の `pages` に1件足す

   ```jsonc
   {
     "slug": "my-page",          // URL になる（/inventory/my-page/）。半角英小文字・数字・ハイフン
     "src": "apps/my-app/dist",  // 配信するフォルダ（リポジトリ直下からのパス）。中の index.html が入口
     "title": "ページ名",
     "description": "ホームとナビに出る一言説明",
     "category": "アプリ",       // ホームではカテゴリごとにまとめて並ぶ
     "listed": true,             // false にするとホームとナビの一覧に出さない（URL を知っていれば開ける）
     "nav": true                 // false にすると、そのページには共通ナビを差し込まない
   }
   ```

2. `.github/workflows/pages.yml` の `on.push.paths` に、`src` のフォルダを足す
   （足さないと、そのフォルダだけを更新したときに配信が動かない）

3. `main` に push する

`src` のフォルダの中の `README.md`・`CLAUDE.md`・`AGENTS.md` とドットファイルは配信しません。

## 手元で確かめる

```sh
python3 site/build.py --out /tmp/pages/inventory
python3 -m http.server -d /tmp/pages 8000
# http://localhost:8000/inventory/ を開く
```

本番と同じ `/inventory/` の下に置くと、リンクやナビの表示を本番と同じ条件で確かめられます。
