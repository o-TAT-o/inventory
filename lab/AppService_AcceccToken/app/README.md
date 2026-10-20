# AppService Easy Auth デモアプリ

Azure AppService の Easy Auth を使ったアクセストークン管理の検証用 Vue3 + Vite アプリです。

## 特徴

- ✅ 3つのトークン管理方式（SessionStorage、Ref状態管理、毎回取得）を切り替えて比較可能
- ✅ 内部APIと外部APIでトークン付与を制御
- ✅ デバッグモードでトークン管理の挙動を詳細に確認
- ✅ CSP (Content Security Policy) を設定してセキュリティ対策

## セットアップ

### 1. 依存関係のインストール

```bash
cd app
npm install
```

### 2. ライブラリのビルド

```bash
cd ../lib/auth-axios
npm install
npm run build
```

### 3. アプリの起動

```bash
cd ../../app
npm run dev
```

ブラウザで `http://localhost:3000` にアクセスします。

## 使い方

1. **トークン管理方式を選択**: SessionStorage、Ref状態管理、毎回取得から選択
2. **APIをテスト**: 各ボタンをクリックしてAPIの動作を確認
3. **デバッグログを確認**: ブラウザの開発者ツールのコンソールで詳細なログを確認
4. **SessionStorage を確認**: 開発者ツールの Application タブで SessionStorage の内容を確認

## Azure AppService へのデプロイ

### Dockerfile の作成

```dockerfile
FROM node:18 AS build
WORKDIR /app

# ライブラリをビルド
COPY lib/auth-axios lib/auth-axios
WORKDIR /app/lib/auth-axios
RUN npm ci && npm run build

# アプリをビルド
WORKDIR /app
COPY app app
WORKDIR /app/app
RUN npm ci && npm run build

# Nginx で配信
FROM nginx:alpine
COPY --from=build /app/app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### デプロイ

```bash
# Docker イメージをビルド
docker build -t appservice-auth-demo .

# Azure Container Registry にプッシュ
az acr login --name <your-acr-name>
docker tag appservice-auth-demo <your-acr-name>.azurecr.io/appservice-auth-demo:latest
docker push <your-acr-name>.azurecr.io/appservice-auth-demo:latest

# App Service にデプロイ
az webapp create --resource-group <rg-name> --plan <plan-name> --name <app-name> --deployment-container-image-name <your-acr-name>.azurecr.io/appservice-auth-demo:latest
```

### Easy Auth の有効化

Azure Portal で以下を設定：

1. App Service → 認証 → ID プロバイダーを追加
2. Microsoft を選択
3. 認証が必要なリクエスト: 認証されていないリクエストをログインにリダイレクト
4. トークン ストア: 有効化

## 注意事項

- ローカル開発環境では /.auth/me が利用できないため、モックサーバーの使用を推奨
- 内部APIと外部APIのエンドポイントは実際の環境に合わせて `App.vue` で調整してください
- CSP が設定されているため、インラインスクリプトは実行できません

## トラブルシューティング

### トークンが取得できない

- Easy Auth が有効化されているか確認
- /.auth/me エンドポイントにアクセスできるか確認
- ブラウザの開発者ツールのネットワークタブで /.auth/me のレスポンスを確認

### API コールが失敗する

- API Management の JWT 検証設定を確認
- Authorization ヘッダーが正しく付与されているか確認
- トークンの有効期限が切れていないか確認
